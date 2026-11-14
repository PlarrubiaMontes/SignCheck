package dev.signcheck.trust;

import dev.signcheck.config.NetworkDataLoaders;
import dev.signcheck.config.SignCheckProperties;
import eu.europa.esig.dss.model.tsl.LOTLInfo;
import eu.europa.esig.dss.model.tsl.TLValidationJobSummary;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.function.OfficialJournalSchemeInformationURI;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Loads the EU <em>List of Trusted Lists</em> (LOTL) and the national Trusted Lists it points to, and keeps
 * them fresh. Active only in {@link TrustMode#LOTL}.
 *
 * <h2>How eIDAS trust works</h2>
 * Each Member State publishes a signed XML <em>Trusted List</em> (ETSI TS 119 612) with its qualified
 * trust service providers and their services (CAs issuing qualified certificates, timestamping
 * authorities...). The European Commission publishes the LOTL, a signed list pointing to every national
 * list and to the certificates allowed to sign each one.
 *
 * <p>Trust must be bootstrapped somewhere: the certificates allowed to sign the LOTL are announced in the
 * Official Journal of the EU. DSS checks that the LOTL is signed by one of them; newer LOTL signing
 * certificates are announced inside "pivot" LOTLs, which DSS follows ({@code pivotSupport}). Lists with an
 * invalid signature or past their next-update date are <b>not</b> synchronised
 * ({@link ExpirationAndSignatureCheckStrategy}), so a tampered list never adds trust anchors.
 *
 * <p>Both Trusted List versions 5 and 6 (ETSI TS 119 612 v2.4.1, mandatory since April 2026) are accepted.
 *
 * <h2>Loading</h2>
 * Downloads go through a file cache ({@code signcheck.trust.cache-dir}). At startup the job first loads
 * whatever is in the cache (offline, fast), then downloads fresh copies, in a background thread so the API
 * answers immediately ({@code trust/status} reports {@code loading: true} meanwhile). Afterwards the lists
 * are refreshed on {@code signcheck.trust.refresh-cron}.
 */
@Service
@ConditionalOnProperty(prefix = "signcheck.trust", name = "mode", havingValue = "LOTL")
public class TrustListService {

    private static final Logger LOG = LoggerFactory.getLogger(TrustListService.class);

    private final TLValidationJob job;
    private final TrustedListsCertificateSource trustedLists;
    private final Executor executor;

    private volatile boolean loading;
    private volatile Instant lastSuccessfulRefresh;

    public TrustListService(SignCheckProperties properties, TrustAnchors trustAnchors, NetworkDataLoaders loaders,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        SignCheckProperties.Trust trust = properties.trust();
        this.trustedLists = trustAnchors.trustedLists()
                .orElseThrow(() -> new IllegalStateException("Trusted Lists source missing in LOTL mode"));
        this.executor = executor;

        LOTLSource lotl = new LOTLSource();
        lotl.setUrl(trust.lotlUrl().toString());
        lotl.setCertificateSource(KeystoreCertificates.read(trust.lotlKeystore(), "signcheck.trust.lotl-keystore"));
        lotl.setSigningCertificatesAnnouncementPredicate(
                new OfficialJournalSchemeInformationURI(trust.officialJournalUrl().toString()));
        lotl.setPivotSupport(true);
        lotl.setTLVersions(List.of(5, 6));

        File cacheDir = trust.cacheDir().toFile();
        FileCacheDataLoader online = new FileCacheDataLoader(loaders.http());
        online.setFileCacheDirectory(cacheDir);
        online.setCacheExpirationTime(0); // always download, keep the copy for offline use
        FileCacheDataLoader offline = new FileCacheDataLoader(new IgnoreDataLoader());
        offline.setFileCacheDirectory(cacheDir);
        offline.setCacheExpirationTime(-1); // cache never expires when reading offline

        this.job = new TLValidationJob();
        job.setListOfTrustedListSources(lotl);
        job.setOnlineDataLoader(online);
        job.setOfflineDataLoader(offline);
        job.setTrustedListCertificateSource(trustedLists);
        job.setSynchronizationStrategy(new ExpirationAndSignatureCheckStrategy());
    }

    /** Starts the initial load in the background once the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        loading = true;
        executor.execute(() -> {
            try {
                job.offlineRefresh();
                refresh();
            } catch (RuntimeException e) {
                LOG.error("Initial Trusted Lists load failed", e);
            } finally {
                loading = false;
            }
        });
    }

    /** Downloads the LOTL and Trusted Lists again and synchronises the trust anchors. */
    @Scheduled(cron = "${signcheck.trust.refresh-cron}")
    public synchronized void refresh() {
        LOG.info("Refreshing Trusted Lists");
        job.onlineRefresh();
        TLValidationJobSummary summary = job.getSummary();
        if (allListsOfTrustedListsValid(summary)) {
            lastSuccessfulRefresh = Instant.now();
        }
        LOG.info("Trusted Lists refreshed: {} valid lists, {} trusted certificates",
                validTrustedListCount(summary), trustedLists.getNumberOfCertificates());
    }

    /** Snapshot for {@code GET /api/v1/trust/status}. */
    public Status status() {
        return new Status(loading, lastSuccessfulRefresh, validTrustedListCount(job.getSummary()));
    }

    /**
     * @param loading               whether the initial load is still running
     * @param lastSuccessfulRefresh last time the LOTL was downloaded with a valid signature; {@code null} if never
     * @param trustedListCount      number of national Trusted Lists with a valid signature
     */
    public record Status(boolean loading, Instant lastSuccessfulRefresh, int trustedListCount) {
    }

    private static boolean allListsOfTrustedListsValid(TLValidationJobSummary summary) {
        List<LOTLInfo> lotls = summary == null ? List.of() : summary.getLOTLInfos();
        return !lotls.isEmpty() && lotls.stream().allMatch(lotl -> lotl.getValidationCacheInfo().isValid());
    }

    private static int validTrustedListCount(TLValidationJobSummary summary) {
        if (summary == null) {
            return 0;
        }
        return (int) summary.getLOTLInfos().stream()
                .flatMap(lotl -> lotl.getTLInfos().stream())
                .filter(tl -> tl.getValidationCacheInfo().isValid())
                .count();
    }
}
