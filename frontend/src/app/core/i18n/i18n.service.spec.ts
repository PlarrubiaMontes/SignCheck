import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';
import { en, es } from './translations';

describe('I18nService', () => {
  beforeEach(() => localStorage.clear());

  function create(): I18nService {
    return TestBed.inject(I18nService);
  }

  it('translates and fills placeholders', () => {
    const i18n = create();
    i18n.use('en');

    expect(i18n.t('result.summary', { valid: 1, count: 2 })).toBe('1 of 2 signature(s) valid');
  });

  it('switches language at runtime and updates <html lang>', () => {
    const i18n = create();
    i18n.use('es');
    TestBed.tick();

    expect(i18n.t('upload.submit')).toBe('Validar');
    expect(document.documentElement.lang).toBe('es');
  });

  it('remembers the chosen language', () => {
    create().use('es');
    TestBed.tick();

    expect(localStorage.getItem('signcheck.language')).toBe('es');
  });

  it('starts with the remembered language', () => {
    localStorage.setItem('signcheck.language', 'es');

    expect(create().language()).toBe('es');
  });

  it('has a Spanish text for every English key', () => {
    expect(Object.keys(es).sort()).toEqual(Object.keys(en).sort());
    expect(Object.values(es).every((text) => text.trim().length > 0)).toBe(true);
  });
});
