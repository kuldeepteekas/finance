import { createReducer, on } from '@ngrx/store';
import { AccountResponse } from '../../core/models/account.model';
import {
  loadAccounts,
  loadAccountsSuccess,
  loadAccountsFailure,
  loadAccount,
  loadAccountSuccess,
  loadAccountFailure
} from './accounts.actions';

export interface AccountsState {
  accounts: AccountResponse[];
  selectedAccount: AccountResponse | null;
  loading: boolean;
  error: string | null;
}

export const initialAccountsState: AccountsState = {
  accounts: [],
  selectedAccount: null,
  loading: false,
  error: null
};

export const accountsReducer = createReducer(
  initialAccountsState,

  on(loadAccounts, (state) => ({
    ...state,
    loading: true,
    error: null
  })),

  on(loadAccountsSuccess, (state, { accounts }) => ({
    ...state,
    accounts,
    loading: false,
    error: null
  })),

  on(loadAccountsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),

  on(loadAccount, (state) => ({
    ...state,
    loading: true,
    error: null
  })),

  on(loadAccountSuccess, (state, { account }) => ({
    ...state,
    selectedAccount: account,
    loading: false,
    error: null
  })),

  on(loadAccountFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  }))
);
