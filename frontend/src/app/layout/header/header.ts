import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, interval, of, startWith, switchMap, takeWhile } from 'rxjs';
import { TrustStatus } from '../../core/api/models';
import { TrustApiService } from '../../core/api/trust-api.service';
import { I18nService, LANGUAGES, Language } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/** Polling interval while the backend is still loading the EU trusted lists. */
export const TRUST_POLL_MS = 10_000;

/** App header: title, trust status indicator and language switcher. */
@Component({
  selector: 'app-header',
  imports: [RouterLink, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  private readonly i18n = inject(I18nService);

  protected readonly languages = LANGUAGES;
  protected readonly language = this.i18n.language;
  protected readonly status = signal<TrustStatus | null | 'error'>(null);

  /** Text of the trust indicator; recomputed when the status or the language changes. */
  protected readonly trustText = computed(() => {
    const status = this.status();
    this.language();
    if (status === null) {
      return '';
    }
    if (status === 'error') {
      return this.i18n.t('trust.unavailable');
    }
    switch (status.mode) {
      case 'LOTL':
        if (status.loading) {
          return this.i18n.t('trust.loading');
        }
        return status.lastSuccessfulRefresh
          ? this.i18n.t('trust.lotl', { time: this.time(status.lastSuccessfulRefresh) })
          : this.i18n.t('trust.lotlNever');
      case 'KEYSTORE':
        return this.i18n.t('trust.keystore', { count: status.trustedCertificateCount });
      default:
        return this.i18n.t('trust.none');
    }
  });

  constructor() {
    const api = inject(TrustApiService);
    // Poll while the trusted lists are loading, then stop
    interval(TRUST_POLL_MS)
      .pipe(
        startWith(0), // first request right away
        switchMap(() => api.status().pipe(catchError(() => of('error' as const)))),
        takeWhile((status) => status !== 'error' && status.loading, true),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((status) => this.status.set(status));
  }

  protected switchTo(language: Language): void {
    this.i18n.use(language);
  }

  private time(iso: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'short', timeStyle: 'short' })
      .format(new Date(iso));
  }
}
