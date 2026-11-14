package dev.signcheck.trust;

import dev.signcheck.trust.dto.TrustStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Reports which trust anchors the validator is using right now. */
@Service
public class TrustStatusService {

    private final TrustAnchors trustAnchors;
    private final ObjectProvider<TrustListService> trustListService;

    public TrustStatusService(TrustAnchors trustAnchors, ObjectProvider<TrustListService> trustListService) {
        this.trustAnchors = trustAnchors;
        this.trustListService = trustListService;
    }

    public TrustStatus status() {
        TrustListService lists = trustListService.getIfAvailable();
        TrustListService.Status lotl = lists == null ? new TrustListService.Status(false, null, 0) : lists.status();
        return new TrustStatus(trustAnchors.mode(), lotl.loading(), lotl.lastSuccessfulRefresh(),
                lotl.trustedListCount(), trustAnchors.trustedCertificateCount());
    }
}
