import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Verdict } from '../../core/api/models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationKey } from '../../core/i18n/translations';

const ICONS: Record<Verdict, string> = { VALID: '✓', INDETERMINATE: '?', INVALID: '✗' };

/** Verdict shown with an icon and a word, so colour is never the only signal. */
@Component({
  selector: 'app-verdict-badge',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge" [class]="'badge badge--' + verdict().toLowerCase()">
      <span class="badge__icon" aria-hidden="true">{{ icon() }}</span>
      {{ labelKey() | t }}
    </span>
  `,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35em;
      padding: 0.2em 0.65em;
      border-radius: 999px;
      font-weight: 600;
      border: 1px solid currentColor;
    }
    .badge__icon { font-weight: 700; }
    .badge--valid { color: var(--valid); background: var(--valid-bg); }
    .badge--indeterminate { color: var(--indeterminate); background: var(--indeterminate-bg); }
    .badge--invalid { color: var(--invalid); background: var(--invalid-bg); }
  `,
})
export class VerdictBadge {
  readonly verdict = input.required<Verdict>();
  protected readonly icon = computed(() => ICONS[this.verdict()]);
  protected readonly labelKey = computed(() => `verdict.${this.verdict()}` as TranslationKey);
}
