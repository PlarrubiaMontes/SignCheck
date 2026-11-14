import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from './models';

/**
 * Normalises any HTTP failure into an {@link ApiProblem}. The backend answers with RFC 9457 ProblemDetail
 * bodies carrying a stable `code`; a status of 0 means the request never reached it.
 */
export function toApiProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK_ERROR', detail: null };
    }
    const body = error.error as { code?: unknown; detail?: unknown } | null;
    return {
      status: error.status,
      code: typeof body?.code === 'string' ? body.code : 'VALIDATION_ERROR',
      detail: typeof body?.detail === 'string' ? body.detail : null,
    };
  }
  return { status: 0, code: 'VALIDATION_ERROR', detail: null };
}

/**
 * Same as {@link toApiProblem} for requests made with `responseType: 'blob'`, whose error body arrives as a
 * Blob that must be read and parsed first.
 */
export async function toApiProblemAsync(error: unknown): Promise<ApiProblem> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    try {
      const body: unknown = JSON.parse(await error.error.text());
      return toApiProblem(new HttpErrorResponse({ status: error.status, error: body }));
    } catch {
      return toApiProblem(new HttpErrorResponse({ status: error.status }));
    }
  }
  return toApiProblem(error);
}
