import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AccountApiService } from '../../core/services/account-api.service';
import {
  loadTransactions,
  loadTransactionsSuccess,
  loadTransactionsFailure,
  loadMoreTransactions,
  loadMoreTransactionsSuccess,
  loadMoreTransactionsFailure
} from './transactions.actions';

export class TransactionsEffects {
  private readonly actions$ = inject(Actions);
  private readonly accountApiService = inject(AccountApiService);

  loadTransactions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadTransactions),
      switchMap(({ accountId }) =>
        this.accountApiService.getTransactions(accountId).pipe(
          map((response) =>
            loadTransactionsSuccess({
              transactions: response.transactions,
              nextCursor: response.nextCursor
            })
          ),
          catchError((error) =>
            of(loadTransactionsFailure({ error: error?.message || 'Failed to load transactions' }))
          )
        )
      )
    )
  );

  loadMoreTransactions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadMoreTransactions),
      switchMap(({ accountId, cursor }) =>
        this.accountApiService.getTransactions(accountId, cursor).pipe(
          map((response) =>
            loadMoreTransactionsSuccess({
              transactions: response.transactions,
              nextCursor: response.nextCursor
            })
          ),
          catchError((error) =>
            of(loadMoreTransactionsFailure({ error: error?.message || 'Failed to load more transactions' }))
          )
        )
      )
    )
  );
}
