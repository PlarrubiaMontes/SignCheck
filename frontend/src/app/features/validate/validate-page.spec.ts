import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { ValidationResponse } from '../../core/api/models';
import { ValidationApiService, ValidationRequest } from '../../core/api/validation-api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { ValidationStore } from '../../core/validation.store';
import { validationResponse } from '../../testing/fixtures';
import { ValidatePage } from './validate-page';

describe('ValidatePage', () => {
  let response: Subject<ValidationResponse>;
  let requests: ValidationRequest[];

  beforeEach(() => {
    response = new Subject();
    requests = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ValidationApiService,
          useValue: { validate: (request: ValidationRequest) => (requests.push(request), response) },
        },
      ],
    });
    TestBed.inject(I18nService).use('en');
  });

  async function render() {
    const fixture = TestBed.createComponent(ValidatePage);
    await fixture.whenStable();
    return fixture;
  }

  function dropFile(element: HTMLElement, name: string, selector = 'app-file-drop .drop') {
    const drop = element.querySelector(selector) as HTMLElement;
    const event = new Event('drop', { bubbles: true, cancelable: true }) as DragEvent;
    Object.defineProperty(event, 'dataTransfer', { value: { files: [new File(['x'], name)] } });
    drop.dispatchEvent(event);
  }

  it('makes the drop zone reachable and operable with the keyboard', async () => {
    const fixture = await render();
    const drop = fixture.nativeElement.querySelector('.drop') as HTMLElement;

    expect(drop.getAttribute('role')).toBe('button');
    expect(drop.getAttribute('tabindex')).toBe('0');
    expect(drop.getAttribute('aria-label')).toContain('Drop the signed file');
  });

  it('disables the submit button until a file is chosen', async () => {
    const fixture = await render();
    const submit = fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    dropFile(fixture.nativeElement, 'signed.pdf');
    await fixture.whenStable();

    expect(submit.disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-testid=selected-file]').textContent).toContain('signed.pdf');
  });

  it('validates, stores the result and opens the result page', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();
    dropFile(fixture.nativeElement, 'signed.pdf');
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[type=submit]').textContent).toContain('Validating');

    response.next(validationResponse());
    await fixture.whenStable();

    expect(requests[0].file.name).toBe('signed.pdf');
    expect(requests[0].originals).toEqual([]);
    expect(TestBed.inject(ValidationStore).result()).toEqual(validationResponse());
    expect(navigate).toHaveBeenCalledWith('/result');
  });

  it('sends the originals only for detached signatures', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await render();
    dropFile(fixture.nativeElement, 'signature.p7s');
    (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    await fixture.whenStable();
    const drops = fixture.nativeElement.querySelectorAll('app-file-drop .drop');
    expect(drops.length).toBe(2);

    const event = new Event('drop', { bubbles: true, cancelable: true }) as DragEvent;
    Object.defineProperty(event, 'dataTransfer', { value: { files: [new File(['o'], 'original.txt')] } });
    (drops[1] as HTMLElement).dispatchEvent(event);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();

    expect(requests[0].originals.map((file) => file.name)).toEqual(['original.txt']);
  });

  it('explains API errors with the message of their code', async () => {
    const fixture = await render();
    dropFile(fixture.nativeElement, 'random.bin');
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();

    response.error(new HttpErrorResponse({ status: 422, error: { code: 'UNSUPPORTED_FORMAT' } }));
    await fixture.whenStable();

    const error = fixture.nativeElement.querySelector('[data-testid=error]') as HTMLElement;
    expect(error.getAttribute('role')).toBe('alert');
    expect(error.textContent).toContain('not a signed document SignCheck can read');
  });
});
