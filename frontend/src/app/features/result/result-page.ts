import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RawReports } from '../../core/api/models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ValidationStore } from '../../core/validation.store';
import { downloadText } from '../../shared/download';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { VerdictBadge } from '../../shared/verdict-badge/verdict-badge';
import { SignatureCard } from './signature-card';

interface ReportDownload {
  key: keyof RawReports;
  name: string;
}

const REPORTS: ReportDownload[] = [
  { key: 'simpleReport', name: 'simple-report' },
  { key: 'detailedReport', name: 'detailed-report' },
  { key: 'diagnosticData', name: 'diagnostic-data' },
  { key: 'etsiValidationReport', name: 'etsi-validation-report' },
];

/** Summary banner, one card per signature and downloads of the JSON result and raw DSS reports. */
@Component({
  selector: 'app-result-page',
  imports: [RouterLink, TranslatePipe, LocalDatePipe, VerdictBadge, SignatureCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './result-page.html',
  styleUrl: './result-page.scss',
})
export class ResultPage {
  protected readonly result = inject(ValidationStore).result;
  protected readonly reports = computed(() => (this.result()?.rawReports ? REPORTS : []));

  protected downloadJson(): void {
    const result = this.result();
    if (result) {
      downloadText('signcheck-result.json', JSON.stringify(result, null, 2), 'application/json');
    }
  }

  protected downloadReport(report: ReportDownload): void {
    const xml = this.result()?.rawReports?.[report.key];
    if (xml) {
      downloadText(`${report.name}.xml`, xml, 'application/xml');
    }
  }
}
