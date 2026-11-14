import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrustStatus } from '../../core/api/models';
import { SigningApiService, SigningOptions } from '../../core/api/signing-api.service';
import { TrustApiService } from '../../core/api/trust-api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Header } from './header';

describe('Header', () => {
  function setup(status: () => ReturnType<TrustApiService['status']>, signing: SigningOptions | null = null) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TrustApiService, useValue: { status } },
        { provide: SigningApiService, useValue: { options$: of(signing) } },
      ],
    });
    TestBed.inject(I18nService).use('en');
  }

  async function render() {
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    return fixture;
  }

  const keystore: TrustStatus = {
    mode: 'KEYSTORE', loading: false, lastSuccessfulRefresh: null, trustedListCount: 0, trustedCertificateCount: 3,
  };

  it('shows the trust status announced to screen readers', async () => {
    setup(() => of(keystore));
    const fixture = await render();

    const trust = fixture.nativeElement.querySelector('[data-testid=trust]') as HTMLElement;
    expect(trust.getAttribute('role')).toBe('status');
    expect(trust.textContent).toContain('Trusting 3 configured certificate(s)');
  });

  it('tells while the EU trusted lists are loading', async () => {
    setup(() => of({ ...keystore, mode: 'LOTL', loading: true }));
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid=trust]').textContent).toContain('Trust lists loading');
    fixture.destroy(); // stops the polling
  });

  it('says when the status cannot be read', async () => {
    setup(() => throwError(() => new Error('down')));
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid=trust]').textContent).toContain('unavailable');
  });

  it('switches the language and marks the active one', async () => {
    setup(() => of(keystore));
    const fixture = await render();
    const spanish = Array.from(fixture.nativeElement.querySelectorAll('button.lang') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === 'ES')!;

    spanish.click();
    await fixture.whenStable();

    expect(spanish.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('[data-testid=trust]').textContent)
      .toContain('Confiando en 3 certificado(s)');
  });

  it('shows the Sign link only when the backend enables signing', async () => {
    setup(() => of(keystore));
    let fixture = await render();
    expect(fixture.nativeElement.textContent).not.toContain('Sign (demo)');

    TestBed.resetTestingModule();
    setup(() => of(keystore), { formats: ['PADES'], levels: ['B'], signer: 'CN=DEMO' });
    fixture = await render();
    expect(fixture.nativeElement.querySelector('nav').textContent).toContain('Sign (demo)');
  });
});
