import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const router = inject(Router);
  const credentials = sessionStorage.getItem('fin_creds');

  let authReq = req;
  if (credentials) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Basic ${credentials}`
      }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        sessionStorage.clear();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
