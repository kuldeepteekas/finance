import { createAction, props } from '@ngrx/store';
import { TransactionResponse } from '../../core/models/account.model';

export const loadTransactions = createAction(
  '[Transactions] Load Transactions',
  props<{ accountId: string }>()
);

export const loadTransactionsSuccess = createAction(
  '[Transactions] Load Transactions Success',
  props<{ transactions: TransactionResponse[]; nextCursor: string | null }>()
);

export const loadTransactionsFailure = createAction(
  '[Transactions] Load Transactions Failure',
  props<{ error: string }>()
);

export const loadMoreTransactions = createAction(
  '[Transactions] Load More Transactions',
  props<{ accountId: string; cursor: string }>()
);

export const loadMoreTransactionsSuccess = createAction(
  '[Transactions] Load More Transactions Success',
  props<{ transactions: TransactionResponse[]; nextCursor: string | null }>()
);

export const loadMoreTransactionsFailure = createAction(
  '[Transactions] Load More Transactions Failure',
  props<{ error: string }>()
);

export const selectTransaction = createAction(
  '[Transactions] Select Transaction',
  props<{ transaction: TransactionResponse }>()
);
