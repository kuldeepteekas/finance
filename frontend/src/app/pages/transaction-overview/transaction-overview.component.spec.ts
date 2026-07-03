import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideMockStore, MockStore } from '@ngrx/store/testing';

import { TransactionOverviewComponent } from './transaction-overview.component';
import { AccountResponse, TransactionResponse } from '../../core/models/account.model';
import { selectSelectedTransaction } from '../../store/transactions/transactions.selectors';
import { selectAllAccounts } from '../../store/accounts/accounts.selectors';

const mockAccount: AccountResponse = {
  id: 'acc-1',
  accountNumber: 'ACC-001',
  accountName: 'My EUR',
  currency: 'EUR',
  balance: 500,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00'
};

const makeTx = (overrides: Partial<TransactionResponse> = {}): TransactionResponse => ({
  id: 'tx-1',
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
  idempotencyKey: 'key-1',
  externalCallStatus: 'SKIPPED',
  createdAt: '2024-06-15T14:30:00',
  ...overrides
});

function buildComponent(accountId: string | null = 'acc-1') {
  TestBed.configureTestingModule({
    imports: [TransactionOverviewComponent, RouterTestingModule],
    providers: [
      provideMockStore({
        selectors: [
          { selector: selectSelectedTransaction, value: makeTx() },
          { selector: selectAllAccounts,         value: [mockAccount] }
        ]
      }),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap(accountId ? { accountId } : {}) }
        }
      }
    ]
  });
  const fixture = TestBed.createComponent(TransactionOverviewComponent);
  fixture.detectChanges();
  return fixture.componentInstance;
}

describe('TransactionOverviewComponent — pure methods', () => {
  // ─── isCredit ──────────────────────────────────────────────────────────────

  describe('isCredit', () => {
    let comp: TransactionOverviewComponent;

    beforeEach(() => {
      comp = buildComponent();
    });

    it('returns true for DEPOSIT', () => {
      expect(comp.isCredit(makeTx({ type: 'DEPOSIT' }))).toBeTrue();
    });

    it('returns true for EXCHANGE_IN', () => {
      expect(comp.isCredit(makeTx({ type: 'EXCHANGE_IN' }))).toBeTrue();
    });

    it('returns true for TRANSFER_IN', () => {
      expect(comp.isCredit(makeTx({ type: 'TRANSFER_IN' }))).toBeTrue();
    });

    it('returns false for WITHDRAWAL', () => {
      expect(comp.isCredit(makeTx({ type: 'WITHDRAWAL' }))).toBeFalse();
    });

    it('returns false for TRANSFER_OUT', () => {
      expect(comp.isCredit(makeTx({ type: 'TRANSFER_OUT' }))).toBeFalse();
    });

    it('returns false for EXCHANGE_OUT', () => {
      expect(comp.isCredit(makeTx({ type: 'EXCHANGE_OUT' }))).toBeFalse();
    });
  });

  // ─── getTypeClass ──────────────────────────────────────────────────────────

  describe('getTypeClass', () => {
    let comp: TransactionOverviewComponent;

    beforeEach(() => {
      comp = buildComponent();
    });

    it('returns badge-success for DEPOSIT', () => {
      expect(comp.getTypeClass('DEPOSIT')).toBe('badge-success');
    });

    it('returns badge-danger for WITHDRAWAL', () => {
      expect(comp.getTypeClass('WITHDRAWAL')).toBe('badge-danger');
    });

    it('returns badge-info for TRANSFER_IN', () => {
      expect(comp.getTypeClass('TRANSFER_IN')).toBe('badge-info');
    });

    it('returns badge-warning for TRANSFER_OUT', () => {
      expect(comp.getTypeClass('TRANSFER_OUT')).toBe('badge-warning');
    });

    it('returns badge-info for EXCHANGE_IN', () => {
      expect(comp.getTypeClass('EXCHANGE_IN')).toBe('badge-info');
    });

    it('returns badge-warning for EXCHANGE_OUT', () => {
      expect(comp.getTypeClass('EXCHANGE_OUT')).toBe('badge-warning');
    });

    it('returns badge-info for unknown type', () => {
      expect(comp.getTypeClass('UNKNOWN')).toBe('badge-info');
    });
  });

  // ─── formatAmount ──────────────────────────────────────────────────────────

  describe('formatAmount', () => {
    let comp: TransactionOverviewComponent;

    beforeEach(() => {
      comp = buildComponent();
    });

    it('formats integer amount with two decimal places', () => {
      expect(comp.formatAmount(100, 'EUR')).toBe('EUR 100.00');
    });

    it('formats large amount with comma thousands separator', () => {
      expect(comp.formatAmount(1500.5, 'USD')).toBe('USD 1,500.50');
    });

    it('formats zero correctly', () => {
      expect(comp.formatAmount(0, 'GBP')).toBe('GBP 0.00');
    });
  });

  // ─── formatDate ────────────────────────────────────────────────────────────

  describe('formatDate', () => {
    let comp: TransactionOverviewComponent;

    beforeEach(() => {
      comp = buildComponent();
    });

    it('returns a non-empty string for a valid ISO date', () => {
      const result = comp.formatDate('2024-06-15T14:30:00');
      expect(result).toBeTruthy();
      expect(typeof result).toBe('string');
    });

    it('includes the year in the formatted date', () => {
      const result = comp.formatDate('2024-06-15T14:30:00');
      expect(result).toContain('2024');
    });
  });

  // ─── accountNumberById ─────────────────────────────────────────────────────

  describe('accountNumberById', () => {
    let comp: TransactionOverviewComponent;

    beforeEach(() => {
      comp = buildComponent();
    });

    it('returns accountNumber when account is found', () => {
      expect(comp.accountNumberById('acc-1')).toBe('ACC-001');
    });

    it('returns the raw id when account is not found', () => {
      expect(comp.accountNumberById('unknown-id')).toBe('unknown-id');
    });

    it('returns N/A for null id', () => {
      expect(comp.accountNumberById(null)).toBe('N/A');
    });
  });

  // ─── goBack ────────────────────────────────────────────────────────────────

  describe('goBack', () => {
    it('navigates to /accounts/:accountId when accountId is in route params', () => {
      const comp = buildComponent('acc-42');
      const router = TestBed.inject(Router);
      const navigateSpy = spyOn(router, 'navigate');

      comp.goBack();

      expect(navigateSpy).toHaveBeenCalledWith(['/accounts', 'acc-42']);
    });

    it('navigates to / when no accountId in route params', () => {
      const comp = buildComponent(null);
      const router = TestBed.inject(Router);
      const navigateSpy = spyOn(router, 'navigate');

      comp.goBack();

      expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });
  });
});
