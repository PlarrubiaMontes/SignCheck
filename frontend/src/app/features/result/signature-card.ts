import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SignatureResult } from '../../core/api/models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { VerdictBadge } from '../../shared/verdict-badge/verdict-badge';

/** One signature: the plain-language essentials, with the technical evidence in a collapsible panel. */
@Component({
  selector: 'app-signature-card',
  imports: [TranslatePipe, LocalDatePipe, VerdictBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signature-card.html',
  styleUrl: './signature-card.scss',
})
export class SignatureCard {
  readonly signature = input.required<SignatureResult>();
  readonly index = input.required<number>();
}
