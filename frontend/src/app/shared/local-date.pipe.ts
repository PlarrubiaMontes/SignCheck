import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';

/** ISO-8601 instant → date and time in the UI language (`dateOnly` for certificate validity dates). */
@Pipe({ name: 'localDate', pure: false })
export class LocalDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(iso: string | null | undefined, dateOnly = false): string {
    if (!iso) {
      return '—';
    }
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
      return iso;
    }
    // Signing times are shown in UTC, like in the reports. (dateStyle/timeStyle cannot be combined with
    // timeZoneName, hence the explicit suffix.)
    const formatted = new Intl.DateTimeFormat(this.i18n.locale(), dateOnly
      ? { dateStyle: 'medium', timeZone: 'UTC' }
      : { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'UTC' },
    ).format(date);
    return dateOnly ? formatted : `${formatted} UTC`;
  }
}
