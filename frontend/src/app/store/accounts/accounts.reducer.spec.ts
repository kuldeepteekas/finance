import { accountsReducer, initialAccountsState, AccountsState } from './accounts.reducer';
import {
  loadAccounts, loadAccountsSuccess, loadAccountsFailure,
  loadAccount, loadAccountSuccess, loadAccountFailure,
  createAccount, createAccountSuccess, createAccountFailure
} from './accounts.actions';
import { AccountResponse } from '../../core/models/account.model';

const mockAccount: AccountResponse = {
  id: 'acc-1',
  accountNumber: '1000000001',
  accountName: 'My Savings',
  currency: 'EUR',
  balance: 1000,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00'
};

describe('accountsReducer', () => {
  it('should return initial state for unknown action', () => {
    const state = accountsReducer(undefined, { type: '@@UNKNOWN' } as any);
    expect(state).toEqual(initialAccountsState);
  });

  // ─── loadAccounts ──────────────────────────────────────────────────────────

  it('loadAccounts: sets loading=true and clears error', () => {
    const prev: AccountsState = { ...initialAccountsState, error: 'old error' };
    const state = accountsReducer(prev, loadAccounts());
    expect(state.loading).toBeTrue();
    expect(state.error).toBeNull();
  });

  // ─── loadAccountsSuccess ───────────────────────────────────────────────────

  it('loadAccountsSuccess: populates accounts, clears loading', () => {
    const prev: AccountsState = { ...initialAccountsState, loading: true };
    const state = accountsReducer(prev, loadAccountsSuccess({ accounts: [mockAccount] }));
    expect(state.accounts).toEqual([mockAccount]);
    expect(state.loading).toBeFalse();
    expect(state.error).toBeNull();
  });

  // ─── loadAccountsFailure ───────────────────────────────────────────────────

  it('loadAccountsFailure: sets error, clears loading', () => {
    const prev: AccountsState = { ...initialAccountsState, loading: true };
    const state = accountsReducer(prev, loadAccountsFailure({ error: 'Network error' }));
    expect(state.loading).toBeFalse();
    expect(state.error).toBe('Network error');
    expect(state.accounts).toEqual([]);
  });

  // ─── loadAccount ───────────────────────────────────────────────────────────

  it('loadAccount: sets loading=true, clears error', () => {
    const prev: AccountsState = { ...initialAccountsState, error: 'stale error' };
    const state = accountsReducer(prev, loadAccount({ id: 'acc-1' }));
    expect(state.loading).toBeTrue();
    expect(state.error).toBeNull();
  });

  // ─── loadAccountSuccess ────────────────────────────────────────────────────

  it('loadAccountSuccess: sets selectedAccount, clears loading', () => {
    const prev: AccountsState = { ...initialAccountsState, loading: true };
    const state = accountsReducer(prev, loadAccountSuccess({ account: mockAccount }));
    expect(state.selectedAccount).toEqual(mockAccount);
    expect(state.loading).toBeFalse();
  });

  // ─── loadAccountFailure ────────────────────────────────────────────────────

  it('loadAccountFailure: sets error, clears loading', () => {
    const prev: AccountsState = { ...initialAccountsState, loading: true };
    const state = accountsReducer(prev, loadAccountFailure({ error: 'Not found' }));
    expect(state.loading).toBeFalse();
    expect(state.error).toBe('Not found');
  });

  // ─── createAccount ─────────────────────────────────────────────────────────

  it('createAccount: sets creating=true, clears error', () => {
    const prev: AccountsState = { ...initialAccountsState, error: 'prev error' };
    const state = accountsReducer(prev, createAccount({ accountName: 'New', currency: 'USD' }));
    expect(state.creating).toBeTrue();
    expect(state.error).toBeNull();
  });

  // ─── createAccountSuccess ──────────────────────────────────────────────────

  it('createAccountSuccess: appends new account to existing list, clears creating', () => {
    const existing = { ...mockAccount, id: 'acc-0' };
    const newAcc   = { ...mockAccount, id: 'acc-new' };
    const prev: AccountsState = { ...initialAccountsState, accounts: [existing], creating: true };
    const state = accountsReducer(prev, createAccountSuccess({ account: newAcc }));
    expect(state.accounts).toEqual([existing, newAcc]);
    expect(state.creating).toBeFalse();
    expect(state.error).toBeNull();
  });

  it('createAccountSuccess: does not mutate previous accounts array', () => {
    const existing = [mockAccount];
    const prev: AccountsState = { ...initialAccountsState, accounts: existing, creating: true };
    const newAcc = { ...mockAccount, id: 'acc-new' };
    const state = accountsReducer(prev, createAccountSuccess({ account: newAcc }));
    // original array untouched (immutability)
    expect(existing.length).toBe(1);
    expect(state.accounts.length).toBe(2);
  });

  // ─── createAccountFailure ──────────────────────────────────────────────────

  it('createAccountFailure: sets error, clears creating', () => {
    const prev: AccountsState = { ...initialAccountsState, creating: true };
    const state = accountsReducer(prev, createAccountFailure({ error: 'Duplicate name' }));
    expect(state.creating).toBeFalse();
    expect(state.error).toBe('Duplicate name');
  });
});
