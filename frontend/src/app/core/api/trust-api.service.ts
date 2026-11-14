import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TrustStatus } from './models';

/** Client of `GET /api/v1/trust/status`. */
@Injectable({ providedIn: 'root' })
export class TrustApiService {
  private readonly http = inject(HttpClient);

  status(): Observable<TrustStatus> {
    return this.http.get<TrustStatus>('/api/v1/trust/status');
  }
}
