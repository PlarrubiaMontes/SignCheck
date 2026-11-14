import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { toApiProblem, toApiProblemAsync } from '../../core/api/api-problem';
import {
  PACKAGINGS,
  SignatureFormat,
  SignatureLevel,
  SignaturePackaging,
  SigningApiService,
} from '../../core/api/signing-api.service';
import { ValidationApiService } from '../../core/api/validation-api.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationKey } from '../../core/i18n/translations';
import { ValidationStore } from '../../core/validation.store';
import { FileDrop } from '../../shared/file-drop/file-drop';
import { FileSizePipe } from '../../shared/file-size.pipe';

const KNOWN_ERRORS = new Set(['NO_FILE', 'FILE_TOO_LARGE', 'INVALID_SIGNING_REQUEST', 'NETWORK_ERROR']);

/** Demo signing page: choose format, packaging and level, download the result, optionally validate it. */
@Component({
  selector: 'app-sign-page',
  imports: [FileDrop, FileSizePipe, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sign-page.html',
  styleUrl: './sign-page.scss',
})
export class SignPage {
  private readonly signingApi = inject(SigningApiService);
  private readonly validationApi = inject(ValidationApiService);
  private readonly store = inject(ValidationStore);
  private readonly router = inject(Router);

  /** `undefined` while loading, `null` when signing is disabled on the server. */
  protected readonly options = toSignal(this.signingApi.options$);

  protected readonly file = signal<File | null>(null);
  protected readonly format = signal<SignatureFormat>('PADES');
  protected readonly packaging = signal<SignaturePackaging>('ENVELOPED');
  protected readonly level = signal<SignatureLevel>('B');
  protected readonly busy = signal(false);
  protected readonly errorKey = signal<TranslationKey | null>(null);
  protected readonly signed = signal<File | null>(null);

  protected readonly packagings = computed(() => PACKAGINGS[this.format()]);
  protected readonly canSubmit = computed(() => this.file() !== null && !this.busy());

  protected chooseFile(files: File[]): void {
    this.file.set(files[0] ?? null);
    this.signed.set(null);
    this.errorKey.set(null);
  }

  protected chooseFormat(format: SignatureFormat): void {
    this.format.set(format);
    this.packaging.set(PACKAGINGS[format][0]);
  }

  protected key(prefix: string, value: string): TranslationKey {
    return `${prefix}.${value}` as TranslationKey;
  }

  protected submit(): void {
    const file = this.file();
    if (!file) {
      this.errorKey.set('error.NO_FILE');
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.signingApi
      .sign({ file, format: this.format(), packaging: this.packaging(), level: this.level() })
      .subscribe({
        next: (signed) => {
          this.busy.set(false);
          this.signed.set(signed);
          this.download(signed);
        },
        error: async (error: unknown) => {
          this.busy.set(false);
          this.showError((await toApiProblemAsync(error)).code);
        },
      });
  }

  protected download(file: File): void {
    const url = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.name;
    link.click();
    URL.revokeObjectURL(url);
  }

  /** Round trip: send the signed file (and the original for detached signatures) to the validator. */
  protected validateNow(): void {
    const signed = this.signed();
    const original = this.file();
    if (!signed || !original) {
      return;
    }
    this.busy.set(true);
    this.validationApi
      .validate({
        file: signed,
        originals: this.packaging() === 'DETACHED' ? [original] : [],
        includeRawReports: false,
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.store.set(result);
          void this.router.navigateByUrl('/result');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.showError(toApiProblem(error).code);
        },
      });
  }

  private showError(code: string): void {
    this.errorKey.set(`error.${KNOWN_ERRORS.has(code) ? code : 'VALIDATION_ERROR'}` as TranslationKey);
  }
}
