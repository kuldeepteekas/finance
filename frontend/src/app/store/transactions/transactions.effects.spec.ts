import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { ReplaySubject, of, throwError } from 'rxjs';
import { take } from 'rxjs/operators';

import { TransactionsEffects } from './transactions.effects';
import { AccountApiService } from '../../core/services/account-api.service';
import {
  loadTransactions, loadTransactionsSuccess, loadTransactionsFailure,
  loadMoreTransactions, loadMoreTransactionsSuccess, loadMoreTransactionsFailure
} from './transactions.actions';
import { TransactionPageResponse, TransactionResponse } from '../../core/models/account.model';

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

describe('TransactionsEffects', () => {
  let actions$: ReplaySubject<Action>;
  let effects: TransactionsEffects;
  let apiService: jasmine.SpyObj<AccountApiService>;

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    apiService = jasmine.createSpyObj('AccountApiService', ['getTransactions']);

    TestBed.configureTestingModule({
      providers: [
        TransactionsEffects,
        provideMockActions(() => actions$),
        { provide: AccountApiService, useValue: apiService }
      ]
    });

    effects = TestBed.inject(TransactionsEffects);
  });

  // ─── loadTransactions$ ─────────────────────────────────────────────────────

  it('loadTransactions$: requests first page with size=7 and no cursor', (done) => {
    const pageResponse: TransactionPageResponse = {
      transactions: [makeTx('tx-1')],
      nextCursor: 'cursor-abc'
    };
    apiService.getTransactions.and.returnValue(of(pageResponse));

    effects.loadTransactions$.pipe(take(1)).subscribe(() => {
      expect(apiService.getTransactions).toHaveBeenCalledWith('acc-1', null, 7);
      done();
    });

    actions$.next(loadTransactions({ accountId: 'acc-1' }));
  });

  it('loadTransactions$: dispatches loadTransactionsSuccess with transactions and cursor', (done) => {
    const pageResponse: TransactionPageResponse = {
      transactions: [makeTx('tx-1'), makeTx('tx-2')],
      nextCursor: 'cursor-abc'
    };
    apiService.getTransactions.and.returnValue(of(pageResponse));

    effects.loadTransactions$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadTransactionsSuccess({
        transactions: [makeTx('tx-1'), makeTx('tx-2')],
        nextCursor: 'cursor-abc'
      }));
      done();
    });

    actions$.next(loadTransactions({ accountId: 'acc-1' }));
  });

  it('loadTransactions$: dispatches loadTransactionsFailure on API error', (done) => {
    apiService.getTransactions.and.returnValue(throwError(() => new Error('Timeout')));

    effects.loadTransactions$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadTransactionsFailure({ error: 'Timeout' }));
      done();
    });

    actions$.next(loadTransactions({ accountId: 'acc-1' }));
  });

  // ─── loadMoreTransactions$ ─────────────────────────────────────────────────

  it('loadMoreTransactions$: requests next page with size=5 and provided cursor', (done) => {
    const pageResponse: TransactionPageResponse = { transactions: [makeTx('tx-3')], nextCursor: null };
    apiService.getTransactions.and.returnValue(of(pageResponse));

    effects.loadMoreTransactions$.pipe(take(1)).subscribe(() => {
      expect(apiService.getTransactions).toHaveBeenCalledWith('acc-1', 'cursor-abc', 5);
      done();
    });

    actions$.next(loadMoreTransactions({ accountId: 'acc-1', cursor: 'cursor-abc' }));
  });

  it('loadMoreTransactions$: dispatches loadMoreTransactionsSuccess on success', (done) => {
    const pageResponse: TransactionPageResponse = {
      transactions: [makeTx('tx-3')],
      nextCursor: 'cursor-next'
    };
    apiService.getTransactions.and.returnValue(of(pageResponse));

    effects.loadMoreTransactions$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadMoreTransactionsSuccess({
        transactions: [makeTx('tx-3')],
        nextCursor: 'cursor-next'
      }));
      done();
    });

    actions$.next(loadMoreTransactions({ accountId: 'acc-1', cursor: 'cursor-abc' }));
  });

  it('loadMoreTransactions$: dispatches loadMoreTransactionsFailure on API error', (done) => {
    apiService.getTransactions.and.returnValue(throwError(() => new Error('Rate limited')));

    effects.loadMoreTransactions$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loadMoreTransactionsFailure({ error: 'Rate limited' }));
      done();
    });

    actions$.next(loadMoreTransactions({ accountId: 'acc-1', cursor: 'cursor-abc' }));
  });
});
