import {
  selectAllTransactions,
  selectTransactionCursor,
  selectHasMore,
  selectTransactionsLoading,
  selectTransactionsLoadingMore,
  selectSelectedTransaction
} from './transactions.selectors';
import { TransactionsState, initialTransactionsState } from './transactions.reducer';
import { TransactionResponse } from '../../core/models/account.model';

const makeTx = (id: string): TransactionResponse => ({
  id,
  accountId: 'acc-1',
  type: 'DEPOSIT',
  amount: 50,
  currency: 'EUR',
  balanceBefore: 0,
  balanceAfter: 50,
  status: 'SUCCESS',
  description: null,
  failureReason: null,
  correlationId: 'corr-1',
  counterpartyAccountId: null,
  idempotencyKey: `key-${id}`,
  externalCallStatus: 'SKIPPED',
  createdAt: '2024-01-01T00:00:00'
});

describe('Transactions Selectors', () => {
  // ─── selectAllTransactions ─────────────────────────────────────────────────

  it('selectAllTransactions: returns empty array from initial state', () => {
    const result = selectAllTransactions.projector(initialTransactionsState);
    expect(result).toEqual([]);
  });

  it('selectAllTransactions: returns all transactions when present', () => {
    const state: TransactionsState = { ...initialTransactionsState, transactions: [makeTx('tx-1')] };
    const result = selectAllTransactions.projector(state);
    expect(result).toEqual([makeTx('tx-1')]);
  });

  // ─── selectTransactionCursor ───────────────────────────────────────────────

  it('selectTransactionCursor: returns null from initial state', () => {
    const result = selectTransactionCursor.projector(initialTransactionsState);
    expect(result).toBeNull();
  });

  it('selectTransactionCursor: returns cursor when set', () => {
    const state: TransactionsState = { ...initialTransactionsState, cursor: 'cursor-xyz' };
    const result = selectTransactionCursor.projector(state);
    expect(result).toBe('cursor-xyz');
  });

  // ─── selectHasMore ─────────────────────────────────────────────────────────

  it('selectHasMore: returns false from initial state', () => {
    const result = selectHasMore.projector(initialTransactionsState);
    expect(result).toBeFalse();
  });

  it('selectHasMore: returns true when there are more pages', () => {
    const state: TransactionsState = { ...initialTransactionsState, hasMore: true };
    const result = selectHasMore.projector(state);
    expect(result).toBeTrue();
  });

  // ─── selectTransactionsLoading ─────────────────────────────────────────────

  it('selectTransactionsLoading: returns false from initial state', () => {
    const result = selectTransactionsLoading.projector(initialTransactionsState);
    expect(result).toBeFalse();
  });

  it('selectTransactionsLoading: returns true when loading', () => {
    const state: TransactionsState = { ...initialTransactionsState, loading: true };
    const result = selectTransactionsLoading.projector(state);
    expect(result).toBeTrue();
  });

  // ─── selectTransactionsLoadingMore ─────────────────────────────────────────

  it('selectTransactionsLoadingMore: returns false from initial state', () => {
    const result = selectTransactionsLoadingMore.projector(initialTransactionsState);
    expect(result).toBeFalse();
  });

  it('selectTransactionsLoadingMore: returns true when loading more', () => {
    const state: TransactionsState = { ...initialTransactionsState, loadingMore: true };
    const result = selectTransactionsLoadingMore.projector(state);
    expect(result).toBeTrue();
  });

  // ─── selectSelectedTransaction ─────────────────────────────────────────────

  it('selectSelectedTransaction: returns null from initial state', () => {
    const result = selectSelectedTransaction.projector(initialTransactionsState);
    expect(result).toBeNull();
  });

  it('selectSelectedTransaction: returns the selected transaction', () => {
    const tx = makeTx('tx-selected');
    const state: TransactionsState = { ...initialTransactionsState, selectedTransaction: tx };
    const result = selectSelectedTransaction.projector(state);
    expect(result).toEqual(tx);
  });
});
