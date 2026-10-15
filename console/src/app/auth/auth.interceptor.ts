import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { Auth } from './auth';

/** Adds the access token to every call to Sentinel's own APIs - and to nothing else. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const auth = inject(Auth);
  return from(auth.token()).pipe(
    switchMap(token => next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))),
  );
};
