import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { TranslationKey, Translations, en, es } from './translations';

export type Language = 'en' | 'es';

export const LANGUAGES: readonly Language[] = ['en', 'es'];

const DICTIONARIES: Record<Language, Translations> = { en, es };
const STORAGE_KEY = 'signcheck.language';

/**
 * Current UI language as a signal. Initial value: the user's previous choice, else the browser language,
 * else English. The same language is sent to the backend as `Accept-Language` (see the interceptor) so the
 * plain-language reasons of the validation match the UI.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Language>(this.initialLanguage());

  readonly language = this.current.asReadonly();
  private readonly dictionary = computed(() => DICTIONARIES[this.current()]);

  constructor() {
    effect(() => {
      const language = this.current();
      this.document.documentElement.lang = language;
      try {
        localStorage.setItem(STORAGE_KEY, language);
      } catch {
        // storage unavailable (private mode...): the choice just is not remembered
      }
    });
  }

  use(language: Language): void {
    this.current.set(language);
  }

  /** Translated text for `key`, with `{name}` placeholders replaced by `params`. */
  t(key: TranslationKey, params: Record<string, string | number> = {}): string {
    return this.dictionary()[key].replace(/\{(\w+)\}/g, (match, name: string) =>
      name in params ? String(params[name]) : match,
    );
  }

  /** Locale for date and number formatting. */
  locale(): string {
    return this.current() === 'es' ? 'es-ES' : 'en-GB';
  }

  private initialLanguage(): Language {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'en' || stored === 'es') {
        return stored;
      }
    } catch {
      // ignore
    }
    const browser = this.document.defaultView?.navigator.language ?? 'en';
    return browser.toLowerCase().startsWith('es') ? 'es' : 'en';
  }
}
