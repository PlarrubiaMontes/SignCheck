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
