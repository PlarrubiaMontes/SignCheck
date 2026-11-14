import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { ValidationStore } from '../../core/validation.store';
import { signature, validationResponse } from '../../testing/fixtures';
import { ResultPage } from './result-page';

describe('ResultPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    TestBed.inject(I18nService).use('en');
  });

  async function render() {
    const fixture = TestBed.createComponent(ResultPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('invites to validate a document when there is no result', async () => {
    const page = await render();

    expect(page.querySelector('[data-testid=empty]')?.textContent).toContain('no result');
  });

  it('shows the summary and one card per signature with its reason', async () => {
    TestBed.inject(ValidationStore).set(validationResponse({
      summary: { signatureCount: 2, validCount: 1, overallVerdict: 'INVALID' },
      signatures: [
        signature(),
        signature({ id: 'S-2', verdict: 'INVALID', subIndication: 'HASH_FAILURE',
          reason: 'The document was modified after it was signed.' }),
      ],
    }));

    const page = await render();

    const banner = page.querySelector('[data-testid=banner]') as HTMLElement;
    expect(banner.classList).toContain('banner--invalid');
    expect(banner.textContent).toContain('1 of 2 signature(s) valid');
    expect(page.querySelectorAll('app-signature-card').length).toBe(2);
    expect(page.querySelector('[data-testid=reason]')?.textContent).toContain('modified after it was signed');
    expect(page.querySelector('[data-testid=signer]')?.textContent).toContain('TEST SIGNER Valid');
    expect(page.textContent).toContain('HASH_FAILURE');
  });

  it('offers raw report downloads only when they were requested', async () => {
    const store = TestBed.inject(ValidationStore);
    store.set(validationResponse());
    let page = await render();
    expect(page.textContent).not.toContain('simple-report');

    store.set(validationResponse({
      rawReports: { simpleReport: '<a/>', detailedReport: '<b/>', diagnosticData: '<c/>', etsiValidationReport: '<d/>' },
    }));
    page = await render();
    expect(page.textContent).toContain('Download simple-report (XML)');
    expect(page.textContent).toContain('Download etsi-validation-report (XML)');
  });
});
