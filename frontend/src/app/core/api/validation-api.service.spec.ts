import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { acceptLanguageInterceptor } from '../i18n/accept-language.interceptor';
import { I18nService } from '../i18n/i18n.service';
import { validationResponse } from '../../testing/fixtures';
import { ValidationApiService } from './validation-api.service';
import { TrustApiService } from './trust-api.service';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([acceptLanguageInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the signed file, the originals and the raw reports flag as multipart', () => {
    const file = new File(['signed'], 'signed.p7s');
    const original = new File(['original'], 'original.txt');
    let result: unknown;

    TestBed.inject(ValidationApiService)
      .validate({ file, originals: [original], includeRawReports: true })
      .subscribe((response) => (result = response));

    const request = http.expectOne('/api/v1/validations');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect((body.get('file') as File).name).toBe('signed.p7s');
    expect((body.getAll('originals') as File[]).map((f) => f.name)).toEqual(['original.txt']);
    expect(body.get('includeRawReports')).toBe('true');

    request.flush(validationResponse());
    expect(result).toEqual(validationResponse());
  });

  it('sends the UI language as Accept-Language', () => {
    TestBed.inject(I18nService).use('es');

    TestBed.inject(TrustApiService).status().subscribe();

    const request = http.expectOne('/api/v1/trust/status');
    expect(request.request.headers.get('Accept-Language')).toBe('es');
    request.flush({});
  });
});
