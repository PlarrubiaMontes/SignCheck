import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ValidationResponse } from './models';

export interface ValidationRequest {
  file: File;
  originals: File[];
  includeRawReports: boolean;
}

/** Client of `POST /api/v1/validations` (multipart: `file`, `originals`, `includeRawReports`). */
@Injectable({ providedIn: 'root' })
export class ValidationApiService {
  private readonly http = inject(HttpClient);

  validate(request: ValidationRequest): Observable<ValidationResponse> {
    const body = new FormData();
    body.append('file', request.file, request.file.name);
    for (const original of request.originals) {
      body.append('originals', original, original.name);
    }
    body.append('includeRawReports', String(request.includeRawReports));
    return this.http.post<ValidationResponse>('/api/v1/validations', body);
  }
}
