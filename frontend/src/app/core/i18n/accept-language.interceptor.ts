import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { I18nService } from './i18n.service';

/** Asks the backend for texts (e.g. validation reasons) in the UI language. */
export const acceptLanguageInterceptor: HttpInterceptorFn = (request, next) => {
  const language = inject(I18nService).language();
  return next(request.clone({ setHeaders: { 'Accept-Language': language } }));
};
