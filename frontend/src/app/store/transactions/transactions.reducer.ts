import { createReducer, on } from '@ngrx/store';
import { TransactionResponse } from '../../core/models/account.model';
import {
  loadTransactions,
  loadTransactionsSuccess,
  loadTransactionsFailure,
  loadMoreTransactions,
  loadMoreTransactionsSuccess,
  loadMoreTransactionsFailure,
  selectTransaction
} from './transactions.actions';

export interface TransactionsState {
  transactions: TransactionResponse[];
  cursor: string | null;
  hasMore: boolean;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  selectedTransaction: TransactionResponse | null;
}

export const initialTransactionsState: TransactionsState = {
  transactions: [],
  cursor: null,
  hasMore: false,
  loading: false,
  loadingMore: false,
  error: null,
  selectedTransaction: null
};

export const transactionsReducer = createReducer(
  initialTransactionsState,

  on(loadTransactions, (state) => ({
    ...state,
    transactions: [],
    cursor: null,
    hasMore: false,
    loading: true,
    loadingMore: false,
    error: null
  })),

  on(loadTransactionsSuccess, (state, { transactions, nextCursor }) => ({
    ...state,
    transactions,
    cursor: nextCursor,
    hasMore: nextCursor !== null,
    loading: false,
    error: null
  })),

  on(loadTransactionsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),

  on(loadMoreTransactions, (state) => ({
    ...state,
    loadingMore: true,
    error: null
  })),

  on(loadMoreTransactionsSuccess, (state, { transactions, nextCursor }) => ({
    ...state,
    transactions: [...state.transactions, ...transactions],
    cursor: nextCursor,
    hasMore: nextCursor !== null,
    loadingMore: false,
    error: null
  })),

  on(loadMoreTransactionsFailure, (state, { error }) => ({
    ...state,
    loadingMore: false,
    error
  })),

  on(selectTransaction, (state, { transaction }) => ({
    ...state,
    selectedTransaction: transaction
  }))
);
