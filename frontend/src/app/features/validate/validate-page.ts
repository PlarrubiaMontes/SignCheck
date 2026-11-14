import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toApiProblem } from '../../core/api/api-problem';
import { ValidationApiService } from '../../core/api/validation-api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationKey } from '../../core/i18n/translations';
import { ValidationStore } from '../../core/validation.store';
import { FileDrop } from '../../shared/file-drop/file-drop';
import { FileSizePipe } from '../../shared/file-size.pipe';

const KNOWN_ERRORS = new Set([
  'NO_FILE', 'FILE_TOO_LARGE', 'NO_SIGNATURE_FOUND', 'UNSUPPORTED_FORMAT', 'VALIDATION_ERROR', 'NETWORK_ERROR',
]);

/** Upload page: the signed file, optional originals for detached signatures, then validate. */
@Component({
  selector: 'app-validate-page',
  imports: [FileDrop, FileSizePipe, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './validate-page.html',
  styleUrl: './validate-page.scss',
})
export class ValidatePage {
  private readonly api = inject(ValidationApiService);
  private readonly store = inject(ValidationStore);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected readonly file = signal<File | null>(null);
  protected readonly detached = signal(false);
  protected readonly originals = signal<File[]>([]);
  protected readonly includeRawReports = signal(false);
  protected readonly loading = signal(false);
  protected readonly errorKey = signal<TranslationKey | null>(null);

  protected readonly canSubmit = computed(() => this.file() !== null && !this.loading());

  protected chooseFile(files: File[]): void {
    this.file.set(files[0] ?? null);
    this.errorKey.set(null);
  }

  protected addOriginals(files: File[]): void {
    this.originals.update((current) => [...current, ...files]);
  }

  protected removeOriginal(index: number): void {
    this.originals.update((current) => current.filter((_, i) => i !== index));
  }

  protected submit(): void {
    const file = this.file();
    if (!file) {
      this.errorKey.set('error.NO_FILE');
      return;
    }
    this.loading.set(true);
    this.errorKey.set(null);
    this.api
      .validate({
        file,
        originals: this.detached() ? this.originals() : [],
        includeRawReports: this.includeRawReports(),
      })
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          this.store.set(result);
          void this.router.navigateByUrl('/result');
        },
        error: (error: unknown) => {
          this.loading.set(false);
          const code = toApiProblem(error).code;
          this.errorKey.set(`error.${KNOWN_ERRORS.has(code) ? code : 'VALIDATION_ERROR'}` as TranslationKey);
        },
      });
  }

  protected selectedText(file: File, size: string): string {
    return this.i18n.t('upload.selected', { name: file.name, size });
  }
}
