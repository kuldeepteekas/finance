import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TransactionsState } from './transactions.reducer';

export const selectTransactionsState = createFeatureSelector<TransactionsState>('transactions');

export const selectAllTransactions = createSelector(
  selectTransactionsState,
  (state) => state.transactions
);

export const selectTransactionCursor = createSelector(
  selectTransactionsState,
  (state) => state.cursor
);

export const selectHasMore = createSelector(
  selectTransactionsState,
  (state) => state.hasMore
);

export const selectTransactionsLoading = createSelector(
  selectTransactionsState,
  (state) => state.loading
);

export const selectTransactionsLoadingMore = createSelector(
  selectTransactionsState,
  (state) => state.loadingMore
);

export const selectSelectedTransaction = createSelector(
  selectTransactionsState,
  (state) => state.selectedTransaction
);
