import { HttpErrorResponse } from '@angular/common/http';
import { toApiProblem } from './api-problem';

describe('toApiProblem', () => {
  it('reads the code and detail of a ProblemDetail body', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { code: 'UNSUPPORTED_FORMAT', detail: 'The file is not a supported signed document' },
    });

    expect(toApiProblem(error)).toEqual({
      status: 422,
      code: 'UNSUPPORTED_FORMAT',
      detail: 'The file is not a supported signed document',
    });
  });

  it('reports a network error when the server was not reached', () => {
    expect(toApiProblem(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK_ERROR');
  });

  it('falls back to VALIDATION_ERROR for bodies without code', () => {
    expect(toApiProblem(new HttpErrorResponse({ status: 502, error: 'Bad gateway' })).code).toBe('VALIDATION_ERROR');
  });
});
