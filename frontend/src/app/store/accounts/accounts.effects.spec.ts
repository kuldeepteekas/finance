import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { ReplaySubject, of, throwError } from 'rxjs';
import { take } from 'rxjs/operators';

import { AccountsEffects } from './accounts.effects';
import { AccountApiService } from '../../core/services/account-api.service';
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

describe('AccountsEffects', () => {
  let actions$: ReplaySubject<Action>;
  let effects: AccountsEffects;
  let apiService: jasmine.SpyObj<AccountApiService>;

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    apiService = jasmine.createSpyObj('AccountApiService', [
      'getAccounts', 'getAccount', 'createAccount'
    ]);

    TestBed.configureTestingModule({
      providers: [
        AccountsEffects,
        provideMockActions(() => actions$),
        { provide: AccountApiService, useValue: apiService }
      ]
    });

    effects = TestBed.inject(AccountsEffects);
  });

  // ─── loadAccounts$ ─────────────────────────────────────────────────────────

  it('loadAccounts$: dispatches loadAccountsSuccess on API success', (done) => {
    apiService.getAccounts.and.returnValue(of([mockAccount]));

    effects.loadAccounts$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadAccountsSuccess({ accounts: [mockAccount] }));
      done();
    });

    actions$.next(loadAccounts());
  });

  it('loadAccounts$: dispatches loadAccountsFailure on API error', (done) => {
    apiService.getAccounts.and.returnValue(throwError(() => new Error('Server error')));

    effects.loadAccounts$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadAccountsFailure({ error: 'Server error' }));
      done();
    });

    actions$.next(loadAccounts());
  });

  // ─── loadAccount$ ──────────────────────────────────────────────────────────

  it('loadAccount$: dispatches loadAccountSuccess on API success', (done) => {
    apiService.getAccount.and.returnValue(of(mockAccount));

    effects.loadAccount$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadAccountSuccess({ account: mockAccount }));
      expect(apiService.getAccount).toHaveBeenCalledWith('acc-1');
      done();
    });

    actions$.next(loadAccount({ id: 'acc-1' }));
  });

  it('loadAccount$: dispatches loadAccountFailure on API error', (done) => {
    apiService.getAccount.and.returnValue(throwError(() => new Error('Not found')));

    effects.loadAccount$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadAccountFailure({ error: 'Not found' }));
      done();
    });

    actions$.next(loadAccount({ id: 'acc-1' }));
  });

  // ─── createAccount$ ────────────────────────────────────────────────────────

  it('createAccount$: dispatches createAccountSuccess on API success', (done) => {
    apiService.createAccount.and.returnValue(of(mockAccount));

    effects.createAccount$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(createAccountSuccess({ account: mockAccount }));
      expect(apiService.createAccount).toHaveBeenCalledWith('My Savings', 'EUR');
      done();
    });

    actions$.next(createAccount({ accountName: 'My Savings', currency: 'EUR' }));
  });

  it('createAccount$: dispatches createAccountFailure with backend error message', (done) => {
    const errorResponse = { error: { message: 'Account name already exists' } };
    apiService.createAccount.and.returnValue(throwError(() => errorResponse));

    effects.createAccount$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(createAccountFailure({ error: 'Account name already exists' }));
      done();
    });

    actions$.next(createAccount({ accountName: 'Duplicate', currency: 'USD' }));
  });

  it('createAccount$: falls back to generic message when no structured error', (done) => {
    apiService.createAccount.and.returnValue(throwError(() => ({})));

    effects.createAccount$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(createAccountFailure({ error: 'Failed to create account' }));
      done();
    });

    actions$.next(createAccount({ accountName: 'New', currency: 'USD' }));
  });
});
