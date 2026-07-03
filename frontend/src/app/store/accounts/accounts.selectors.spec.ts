import {
  selectAllAccounts,
  selectSelectedAccount,
  selectAccountsLoading,
  selectAccountsCreating,
  selectAccountsError
} from './accounts.selectors';
import { AccountsState, initialAccountsState } from './accounts.reducer';
import { AccountResponse } from '../../core/models/account.model';

const mockAccount: AccountResponse = {
  id: 'acc-1',
  accountNumber: '1000000001',
  accountName: 'My Savings',
  currency: 'EUR',
  balance: 500,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00'
};

describe('Accounts Selectors', () => {
  // ─── selectAllAccounts ─────────────────────────────────────────────────────

  it('selectAllAccounts: returns empty array from initial state', () => {
    const result = selectAllAccounts.projector(initialAccountsState);
    expect(result).toEqual([]);
  });

  it('selectAllAccounts: returns all accounts when present', () => {
    const state: AccountsState = { ...initialAccountsState, accounts: [mockAccount] };
    const result = selectAllAccounts.projector(state);
    expect(result).toEqual([mockAccount]);
  });

  it('selectAllAccounts: returns multiple accounts in order', () => {
    const second = { ...mockAccount, id: 'acc-2', accountNumber: '1000000002' };
    const state: AccountsState = { ...initialAccountsState, accounts: [mockAccount, second] };
    const result = selectAllAccounts.projector(state);
    expect(result.length).toBe(2);
    expect(result[0].id).toBe('acc-1');
    expect(result[1].id).toBe('acc-2');
  });

  // ─── selectSelectedAccount ─────────────────────────────────────────────────

  it('selectSelectedAccount: returns null from initial state', () => {
    const result = selectSelectedAccount.projector(initialAccountsState);
    expect(result).toBeNull();
  });

  it('selectSelectedAccount: returns the selected account', () => {
    const state: AccountsState = { ...initialAccountsState, selectedAccount: mockAccount };
    const result = selectSelectedAccount.projector(state);
    expect(result).toEqual(mockAccount);
  });

  // ─── selectAccountsLoading ─────────────────────────────────────────────────

  it('selectAccountsLoading: returns false from initial state', () => {
    const result = selectAccountsLoading.projector(initialAccountsState);
    expect(result).toBeFalse();
  });

  it('selectAccountsLoading: returns true when loading', () => {
    const state: AccountsState = { ...initialAccountsState, loading: true };
    const result = selectAccountsLoading.projector(state);
    expect(result).toBeTrue();
  });

  // ─── selectAccountsCreating ────────────────────────────────────────────────

  it('selectAccountsCreating: returns false from initial state', () => {
    const result = selectAccountsCreating.projector(initialAccountsState);
    expect(result).toBeFalse();
  });

  it('selectAccountsCreating: returns true when creating', () => {
    const state: AccountsState = { ...initialAccountsState, creating: true };
    const result = selectAccountsCreating.projector(state);
    expect(result).toBeTrue();
  });

  // ─── selectAccountsError ───────────────────────────────────────────────────

  it('selectAccountsError: returns null from initial state', () => {
    const result = selectAccountsError.projector(initialAccountsState);
    expect(result).toBeNull();
  });

  it('selectAccountsError: returns error message when set', () => {
    const state: AccountsState = { ...initialAccountsState, error: 'Something went wrong' };
    const result = selectAccountsError.projector(state);
    expect(result).toBe('Something went wrong');
  });
});
