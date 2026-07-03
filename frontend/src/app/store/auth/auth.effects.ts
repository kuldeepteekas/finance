import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { AccountApiService } from '../../core/services/account-api.service';
import { login, loginSuccess, loginFailure, logout } from './auth.actions';

export class AuthEffects {
  private readonly actions$ = inject(Actions);
  private readonly accountApiService = inject(AccountApiService);
  private readonly router = inject(Router);

  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(login),
      switchMap(({ username, password }) => {
        const encoded = btoa(`${username}:${password}`);
        // Temporarily store credentials so the interceptor can attach them
        sessionStorage.setItem('fin_creds', encoded);
        return this.accountApiService.getAccounts().pipe(
          map(() => loginSuccess({ credentials: encoded })),
          catchError((error) => {
            sessionStorage.removeItem('fin_creds');
            const message =
              error?.status === 401
                ? 'Invalid username or password'
                : error?.message || 'Login failed. Please try again.';
            return of(loginFailure({ error: message }));
          })
        );
      })
    )
  );

  loginSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(loginSuccess),
        tap(({ credentials }) => {
          sessionStorage.setItem('fin_creds', credentials);
          this.router.navigate(['/']);
        })
      ),
    { dispatch: false }
  );

  logout$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(logout),
        tap(() => {
          sessionStorage.clear();
          this.router.navigate(['/login']);
        })
      ),
    { dispatch: false }
  );
}
