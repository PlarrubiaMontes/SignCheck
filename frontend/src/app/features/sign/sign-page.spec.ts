import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { SigningApiService, SigningOptions, SigningRequest } from '../../core/api/signing-api.service';
import { ValidationApiService, ValidationRequest } from '../../core/api/validation-api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { ValidationStore } from '../../core/validation.store';
import { validationResponse } from '../../testing/fixtures';
import { SignPage } from './sign-page';

const OPTIONS: SigningOptions = {
  formats: ['PADES', 'XADES', 'CADES'],
  levels: ['B', 'T'],
  signer: 'CN=SignCheck DEMO Signer - TEST ONLY',
};

describe('SignPage', () => {
  let signRequests: SigningRequest[];
  let validateRequests: ValidationRequest[];
  let signResult: () => Observable<File>;

  function setup(options: SigningOptions | null) {
    signRequests = [];
    validateRequests = [];
    signResult = () => of(new File(['signed'], 'contract-signed.pdf', { type: 'application/pdf' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: SigningApiService,
          useValue: {
            options$: of(options),
            sign: (request: SigningRequest) => (signRequests.push(request), signResult()),
          },
        },
        {
          provide: ValidationApiService,
          useValue: {
            validate: (request: ValidationRequest) => (validateRequests.push(request), of(validationResponse())),
          },
        },
      ],
    });
    TestBed.inject(I18nService).use('en');
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  }

  async function render() {
    const fixture = TestBed.createComponent(SignPage);
    await fixture.whenStable();
    return fixture;
  }

  function choose(element: HTMLElement, name: string) {
    const event = new Event('drop', { bubbles: true, cancelable: true }) as DragEvent;
    Object.defineProperty(event, 'dataTransfer', { value: { files: [new File(['x'], name)] } });
    (element.querySelector('app-file-drop .drop') as HTMLElement).dispatchEvent(event);
  }

  function radios(element: HTMLElement, name: string): HTMLInputElement[] {
    return Array.from(element.querySelectorAll(`input[name=${name}]`));
  }

  it('says when signing is disabled on the server', async () => {
    setup(null);
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid=disabled]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('warns that the certificate is a TEST-ONLY demo', async () => {
    setup(OPTIONS);
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid=warning]').textContent)
      .toContain('CN=SignCheck DEMO Signer - TEST ONLY');
  });

  it('only offers the packagings of the selected format', async () => {
    setup(OPTIONS);
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    expect(radios(page, 'packaging').map((r) => r.value)).toEqual(['ENVELOPED']);

    radios(page, 'format').find((r) => r.value === 'CADES')!.click();
    await fixture.whenStable();

    expect(radios(page, 'packaging').map((r) => r.value)).toEqual(['ENVELOPING', 'DETACHED']);
    expect(radios(page, 'packaging')[0].checked).toBe(true);
  });

  it('signs with the chosen options and downloads the result', async () => {
    setup(OPTIONS);
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    choose(page, 'contract.pdf');
    radios(page, 'level').find((r) => r.value === 'T')!.click();
    await fixture.whenStable();

    (page.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(signRequests[0]).toMatchObject({ format: 'PADES', packaging: 'ENVELOPED', level: 'T' });
    expect(signRequests[0].file.name).toBe('contract.pdf');
    expect(page.querySelector('[data-testid=done]')?.textContent).toContain('contract-signed.pdf');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
  });

  it('validates a detached signature together with its original', async () => {
    setup(OPTIONS);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    choose(page, 'original.txt');
    radios(page, 'format').find((r) => r.value === 'XADES')!.click();
    await fixture.whenStable();
    radios(page, 'packaging').find((r) => r.value === 'DETACHED')!.click();
    (page.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await fixture.whenStable();

    (Array.from(page.querySelectorAll('[data-testid=done] button')).at(-1) as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(validateRequests[0].file.name).toBe('contract-signed.pdf');
    expect(validateRequests[0].originals.map((f) => f.name)).toEqual(['original.txt']);
    expect(TestBed.inject(ValidationStore).result()).toEqual(validationResponse());
    expect(navigate).toHaveBeenCalledWith('/result');
  });

  it('explains a rejected combination using the ProblemDetail code from the blob body', async () => {
    setup(OPTIONS);
    signResult = () => throwError(() => new HttpErrorResponse({
      status: 400,
      error: new Blob([JSON.stringify({ code: 'INVALID_SIGNING_REQUEST' })], { type: 'application/problem+json' }),
    }));
    const fixture = await render();
    choose(fixture.nativeElement, 'notes.txt');
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid=error]')?.textContent).toContain('PAdES needs a PDF');
    });
  });
});
