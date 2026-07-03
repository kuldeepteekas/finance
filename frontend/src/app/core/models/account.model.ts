export interface AccountResponse {
  id: string;
  accountNumber: string;
  accountName: string;
  currency: 'EUR' | 'USD' | 'SEK' | 'GBP' | 'VND';
  balance: number;
  status: 'ACTIVE' | 'CLOSED' | 'BLOCKED' | 'INACTIVE';
  createdAt: string;
}

export interface TransactionResponse {
  id: string;
  accountId: string;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'EXCHANGE_OUT' | 'EXCHANGE_IN' | 'TRANSFER_OUT' | 'TRANSFER_IN';
  amount: number;
  currency: string;
  balanceBefore: number;
  balanceAfter: number;
  status: 'SUCCESS' | 'FAILED';
  description: string | null;
  failureReason: string | null;
  correlationId: string;
  counterpartyAccountId: string | null;
  idempotencyKey: string;
  externalCallStatus: 'SUCCESS' | 'FAILED' | 'SKIPPED';
  createdAt: string;
}

export interface TransactionPageResponse {
  transactions: TransactionResponse[];
  nextCursor: string | null;
}

export interface ExchangeRateResponse {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveFrom: string;
}
