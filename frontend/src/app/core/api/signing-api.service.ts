import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

export type SignatureFormat = 'PADES' | 'XADES' | 'CADES';
export type SignaturePackaging = 'ENVELOPED' | 'ENVELOPING' | 'DETACHED';
export type SignatureLevel = 'B' | 'T';

/** Mirror of the backend's `SigningCapabilities`. */
export interface SigningOptions {
  formats: SignatureFormat[];
  levels: SignatureLevel[];
  signer: string;
}

export interface SigningRequest {
  file: File;
  format: SignatureFormat;
  packaging: SignaturePackaging;
  level: SignatureLevel;
}

/** Packagings each format supports (same rules as the backend's SigningService). */
export const PACKAGINGS: Record<SignatureFormat, SignaturePackaging[]> = {
  PADES: ['ENVELOPED'],
  XADES: ['ENVELOPED', 'ENVELOPING', 'DETACHED'],
  CADES: ['ENVELOPING', 'DETACHED'],
};

/** Client of `/api/v1/signatures` (demo signing; the endpoints only exist when the backend enables signing). */
@Injectable({ providedIn: 'root' })
export class SigningApiService {
  private readonly http = inject(HttpClient);

  /** Signing options, or `null` when the backend has signing disabled. Requested once and shared. */
  readonly options$: Observable<SigningOptions | null> = this.http
    .get<SigningOptions>('/api/v1/signatures/options')
    .pipe(
      catchError(() => of(null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

  sign(request: SigningRequest): Observable<File> {
    const body = new FormData();
    body.append('file', request.file, request.file.name);
    body.append('format', request.format);
    body.append('packaging', request.packaging);
    body.append('level', request.level);
    return this.http
      .post('/api/v1/signatures', body, { observe: 'response', responseType: 'blob' })
      .pipe(map((response) => toFile(response, request.file.name)));
  }
}

function toFile(response: HttpResponse<Blob>, fallbackName: string): File {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const name = /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? `signed-${fallbackName}`;
  const blob = response.body ?? new Blob();
  return new File([blob], name, { type: blob.type || response.headers.get('Content-Type') || '' });
}
