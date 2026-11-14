import { TestBed } from '@angular/core/testing';
import { I18nService } from '../core/i18n/i18n.service';
import { FileSizePipe } from './file-size.pipe';
import { VerdictBadge } from './verdict-badge/verdict-badge';

describe('FileSizePipe', () => {
  const pipe = new FileSizePipe();

  it.each([
    [0, '0 B'],
    [512, '512 B'],
    [1536, '1.5 KB'],
    [20 * 1024 * 1024, '20 MB'],
  ])('formats %d bytes as %s', (bytes, expected) => {
    expect(pipe.transform(bytes)).toBe(expected);
  });

  it('returns an empty string for missing values', () => {
    expect(pipe.transform(null)).toBe('');
  });
});

describe('VerdictBadge', () => {
  it('shows an icon and a translated word, not only a colour', async () => {
    TestBed.inject(I18nService).use('es');
    const fixture = TestBed.createComponent(VerdictBadge);
    fixture.componentRef.setInput('verdict', 'INVALID');
    await fixture.whenStable();

    const badge = fixture.nativeElement.querySelector('.badge') as HTMLElement;
    expect(badge.textContent).toContain('✗');
    expect(badge.textContent).toContain('No válida');
    expect(badge.classList).toContain('badge--invalid');
  });
});
