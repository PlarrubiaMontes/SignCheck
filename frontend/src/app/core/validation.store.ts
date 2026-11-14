import { Injectable, signal } from '@angular/core';
import { ValidationResponse } from './api/models';

/**
 * Holds the last validation result in memory so the result page can show it. Nothing is persisted:
 * reloading the page forgets it, like the backend forgets the document.
 */
@Injectable({ providedIn: 'root' })
export class ValidationStore {
  private readonly current = signal<ValidationResponse | null>(null);

  readonly result = this.current.asReadonly();

  set(result: ValidationResponse): void {
    this.current.set(result);
  }

  clear(): void {
    this.current.set(null);
  }
}
