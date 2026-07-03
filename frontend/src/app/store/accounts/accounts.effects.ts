import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AccountApiService } from '../../core/services/account-api.service';
import {
  loadAccounts,
  loadAccountsSuccess,
  loadAccountsFailure,
  loadAccount,
  loadAccountSuccess,
  loadAccountFailure,
  createAccount,
  createAccountSuccess,
  createAccountFailure
} from './accounts.actions';

export class AccountsEffects {
  private readonly actions$ = inject(Actions);
  private readonly accountApiService = inject(AccountApiService);

  loadAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadAccounts),
      switchMap(() =>
        this.accountApiService.getAccounts().pipe(
          map((accounts) => loadAccountsSuccess({ accounts })),
          catchError((error) =>
            of(loadAccountsFailure({ error: error?.message || 'Failed to load accounts' }))
          )
        )
      )
    )
  );

  loadAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadAccount),
      switchMap(({ id }) =>
        this.accountApiService.getAccount(id).pipe(
          map((account) => loadAccountSuccess({ account })),
          catchError((error) =>
            of(loadAccountFailure({ error: error?.message || 'Failed to load account' }))
          )
        )
      )
    )
  );

  createAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(createAccount),
      switchMap(({ accountName, currency }) =>
        this.accountApiService.createAccount(accountName, currency).pipe(
          map((account) => createAccountSuccess({ account })),
          catchError((error) => {
            const msg =
              error?.error?.message ||
              error?.error?.error ||
              error?.message ||
              'Failed to create account';
            return of(createAccountFailure({ error: msg }));
          })
        )
      )
    )
  );
}
