import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AccountResponse,
  TransactionPageResponse,
  ExchangeRateResponse
} from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  getAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>(`${this.baseUrl}/accounts`);
  }

  getAccount(id: string): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${this.baseUrl}/accounts/${id}`);
  }

  getTransactions(
    accountId: string,
    cursor?: string | null,
    size: number = 20
  ): Observable<TransactionPageResponse> {
    let params = new HttpParams().set('size', size.toString());
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<TransactionPageResponse>(
      `${this.baseUrl}/accounts/${accountId}/transactions`,
      { params }
    );
  }

  getExchangeRates(from: string, to?: string): Observable<ExchangeRateResponse | ExchangeRateResponse[]> {
    let params = new HttpParams().set('from', from);
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<ExchangeRateResponse | ExchangeRateResponse[]>(
      `${this.baseUrl}/exchange-rates`,
      { params }
    );
  }
}
