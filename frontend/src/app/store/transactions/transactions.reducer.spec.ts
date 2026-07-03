import { transactionsReducer, initialTransactionsState, TransactionsState } from './transactions.reducer';
import {
  loadTransactions, loadTransactionsSuccess, loadTransactionsFailure,
  loadMoreTransactions, loadMoreTransactionsSuccess, loadMoreTransactionsFailure,
  selectTransaction
} from './transactions.actions';
import { TransactionResponse } from '../../core/models/account.model';

const makeTx = (id: string): TransactionResponse => ({
  id,
  accountId: 'acc-1',
  type: 'DEPOSIT',
  amount: 100,
  currency: 'EUR',
  balanceBefore: 0,
  balanceAfter: 100,
  status: 'SUCCESS',
  description: null,
  failureReason: null,
  correlationId: 'corr-1',
  counterpartyAccountId: null,
  idempotencyKey: `key-${id}`,
  externalCallStatus: 'SKIPPED',
  createdAt: '2024-01-01T00:00:00'
});

describe('transactionsReducer', () => {
  it('should return initial state for unknown action', () => {
    const state = transactionsReducer(undefined, { type: '@@UNKNOWN' } as any);
    expect(state).toEqual(initialTransactionsState);
  });

  // ─── loadTransactions ──────────────────────────────────────────────────────

  it('loadTransactions: resets list, sets loading, clears cursor and hasMore', () => {
    const prev: TransactionsState = {
      ...initialTransactionsState,
      transactions: [makeTx('tx-1')],
      cursor: 'old-cursor',
      hasMore: true,
      error: 'old error'
    };
    const state = transactionsReducer(prev, loadTransactions({ accountId: 'acc-1' }));
    expect(state.transactions).toEqual([]);
    expect(state.cursor).toBeNull();
    expect(state.hasMore).toBeFalse();
    expect(state.loading).toBeTrue();
    expect(state.loadingMore).toBeFalse();
    expect(state.error).toBeNull();
  });

  // ─── loadTransactionsSuccess ───────────────────────────────────────────────

  it('loadTransactionsSuccess: populates transactions, sets cursor, hasMore=true', () => {
    const prev: TransactionsState = { ...initialTransactionsState, loading: true };
    const state = transactionsReducer(prev, loadTransactionsSuccess({
      transactions: [makeTx('tx-1')],
      nextCursor: 'cursor-abc'
    }));
    expect(state.transactions).toEqual([makeTx('tx-1')]);
    expect(state.cursor).toBe('cursor-abc');
    expect(state.hasMore).toBeTrue();
    expect(state.loading).toBeFalse();
    expect(state.error).toBeNull();
  });

  it('loadTransactionsSuccess: sets hasMore=false when nextCursor is null', () => {
    const prev: TransactionsState = { ...initialTransactionsState, loading: true };
    const state = transactionsReducer(prev, loadTransactionsSuccess({
      transactions: [makeTx('tx-1')],
      nextCursor: null
    }));
    expect(state.hasMore).toBeFalse();
    expect(state.cursor).toBeNull();
  });

  // ─── loadTransactionsFailure ───────────────────────────────────────────────

  it('loadTransactionsFailure: sets error, clears loading', () => {
    const prev: TransactionsState = { ...initialTransactionsState, loading: true };
    const state = transactionsReducer(prev, loadTransactionsFailure({ error: 'API down' }));
    expect(state.loading).toBeFalse();
    expect(state.error).toBe('API down');
  });

  // ─── loadMoreTransactions ──────────────────────────────────────────────────

  it('loadMoreTransactions: sets loadingMore=true, clears error', () => {
    const prev: TransactionsState = { ...initialTransactionsState, error: 'old error' };
    const state = transactionsReducer(prev, loadMoreTransactions({ accountId: 'acc-1', cursor: 'cursor-1' }));
    expect(state.loadingMore).toBeTrue();
    expect(state.error).toBeNull();
  });

  // ─── loadMoreTransactionsSuccess ───────────────────────────────────────────

  it('loadMoreTransactionsSuccess: appends new transactions to existing list', () => {
    const existing = [makeTx('tx-1'), makeTx('tx-2')];
    const more     = [makeTx('tx-3')];
    const prev: TransactionsState = { ...initialTransactionsState, transactions: existing, loadingMore: true };
    const state = transactionsReducer(prev, loadMoreTransactionsSuccess({
      transactions: more,
      nextCursor: null
    }));
    expect(state.transactions).toEqual([...existing, ...more]);
    expect(state.loadingMore).toBeFalse();
    expect(state.hasMore).toBeFalse();
  });

  it('loadMoreTransactionsSuccess: sets hasMore=true when nextCursor is present', () => {
    const prev: TransactionsState = { ...initialTransactionsState, loadingMore: true };
    const state = transactionsReducer(prev, loadMoreTransactionsSuccess({
      transactions: [makeTx('tx-3')],
      nextCursor: 'next-cursor'
    }));
    expect(state.hasMore).toBeTrue();
    expect(state.cursor).toBe('next-cursor');
  });

  it('loadMoreTransactionsSuccess: does not mutate existing transactions array', () => {
    const existing = [makeTx('tx-1')];
    const prev: TransactionsState = { ...initialTransactionsState, transactions: existing, loadingMore: true };
    transactionsReducer(prev, loadMoreTransactionsSuccess({ transactions: [makeTx('tx-2')], nextCursor: null }));
    expect(existing.length).toBe(1); // untouched
  });

  // ─── loadMoreTransactionsFailure ───────────────────────────────────────────

  it('loadMoreTransactionsFailure: sets error, clears loadingMore', () => {
    const prev: TransactionsState = { ...initialTransactionsState, loadingMore: true };
    const state = transactionsReducer(prev, loadMoreTransactionsFailure({ error: 'Pagination error' }));
    expect(state.loadingMore).toBeFalse();
    expect(state.error).toBe('Pagination error');
  });

  // ─── selectTransaction ─────────────────────────────────────────────────────

  it('selectTransaction: sets selectedTransaction', () => {
    const tx = makeTx('tx-selected');
    const state = transactionsReducer(initialTransactionsState, selectTransaction({ transaction: tx }));
    expect(state.selectedTransaction).toEqual(tx);
  });

  it('selectTransaction: replacing existing selection', () => {
    const prev: TransactionsState = { ...initialTransactionsState, selectedTransaction: makeTx('tx-old') };
    const newTx = makeTx('tx-new');
    const state = transactionsReducer(prev, selectTransaction({ transaction: newTx }));
    expect(state.selectedTransaction?.id).toBe('tx-new');
  });
});
