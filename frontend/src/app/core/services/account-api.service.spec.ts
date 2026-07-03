import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountApiService } from './account-api.service';
import { environment } from '../../../environments/environment';
import { AccountResponse, TransactionPageResponse } from '../models/account.model';

const BASE = environment.apiUrl;

const mockAccount: AccountResponse = {
  id: 'acc-1',
  accountNumber: '1000000001',
  accountName: 'My Savings',
  currency: 'EUR',
  balance: 1500,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00'
};

describe('AccountApiService', () => {
  let service: AccountApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AccountApiService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify(); // assert no unexpected requests remain
  });

  // ─── getAccounts ───────────────────────────────────────────────────────────

  it('getAccounts: GET /accounts and returns account array', () => {
    service.getAccounts().subscribe((accounts) => {
      expect(accounts).toEqual([mockAccount]);
    });

    const req = http.expectOne(`${BASE}/accounts`);
    expect(req.request.method).toBe('GET');
    req.flush([mockAccount]);
  });

  // ─── getAccount ────────────────────────────────────────────────────────────

  it('getAccount: GET /accounts/:id and returns single account', () => {
    service.getAccount('acc-1').subscribe((account) => {
      expect(account).toEqual(mockAccount);
    });

    const req = http.expectOne(`${BASE}/accounts/acc-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockAccount);
  });

  // ─── createAccount ─────────────────────────────────────────────────────────

  it('createAccount: POST /accounts with accountName and currency in body', () => {
    service.createAccount('My Savings', 'EUR').subscribe((account) => {
      expect(account).toEqual(mockAccount);
    });

    const req = http.expectOne(`${BASE}/accounts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountName: 'My Savings', currency: 'EUR' });
    req.flush(mockAccount);
  });

  // ─── getTransactions ───────────────────────────────────────────────────────

  it('getTransactions: GET with size param, no cursor when null', () => {
    const page: TransactionPageResponse = { transactions: [], nextCursor: null };

    service.getTransactions('acc-1', null, 7).subscribe((resp) => {
      expect(resp).toEqual(page);
    });

    const req = http.expectOne((r) =>
      r.url === `${BASE}/accounts/acc-1/transactions` && r.params.get('size') === '7'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('cursor')).toBeFalse();
    req.flush(page);
  });

  it('getTransactions: includes cursor param when provided', () => {
    const page: TransactionPageResponse = { transactions: [], nextCursor: null };

    service.getTransactions('acc-1', 'cursor-abc', 5).subscribe();

    const req = http.expectOne((r) =>
      r.url === `${BASE}/accounts/acc-1/transactions` &&
      r.params.get('cursor') === 'cursor-abc' &&
      r.params.get('size') === '5'
    );
    req.flush(page);
  });

  // ─── deposit ───────────────────────────────────────────────────────────────

  it('deposit: POST /accounts/:id/deposit with amount and Idempotency-Key header', () => {
    service.deposit('acc-1', 250, 'idem-key-1', 'salary').subscribe();

    const req = http.expectOne(`${BASE}/accounts/acc-1/deposit`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 250, description: 'salary' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-1');
    req.flush({});
  });

  // ─── withdraw ──────────────────────────────────────────────────────────────

  it('withdraw: POST /accounts/:id/withdraw with amount and Idempotency-Key header', () => {
    service.withdraw('acc-1', 100, 'idem-key-2').subscribe();

    const req = http.expectOne(`${BASE}/accounts/acc-1/withdraw`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 100, description: null });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-2');
    req.flush({});
  });

  // ─── transfer ──────────────────────────────────────────────────────────────

  it('transfer: POST /accounts/:id/transfer with target account in body', () => {
    service.transfer('acc-1', 'acc-2', 500, 'idem-key-3', 'rent').subscribe();

    const req = http.expectOne(`${BASE}/accounts/acc-1/transfer`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ toAccountId: 'acc-2', amount: 500, description: 'rent' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-3');
    req.flush([]);
  });
});
