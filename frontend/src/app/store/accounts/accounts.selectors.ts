import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AccountsState } from './accounts.reducer';

export const selectAccountsState = createFeatureSelector<AccountsState>('accounts');

export const selectAllAccounts = createSelector(
  selectAccountsState,
  (state) => state.accounts
);

export const selectSelectedAccount = createSelector(
  selectAccountsState,
  (state) => state.selectedAccount
);

export const selectAccountsLoading = createSelector(
  selectAccountsState,
  (state) => state.loading
);

export const selectAccountsCreating = createSelector(
  selectAccountsState,
  (state) => state.creating
);

export const selectAccountsError = createSelector(
  selectAccountsState,
  (state) => state.error
);
