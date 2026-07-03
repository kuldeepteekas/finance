import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavbarComponent } from '../../shared/components/navbar/navbar.component';
import { loadAccounts, createAccount } from '../../store/accounts/accounts.actions';
import {
  selectAllAccounts,
  selectAccountsLoading,
  selectAccountsCreating,
  selectAccountsError
} from '../../store/accounts/accounts.selectors';
import { AccountResponse } from '../../core/models/account.model';
import { Actions, ofType } from '@ngrx/effects';
import { createAccountSuccess, createAccountFailure } from '../../store/accounts/accounts.actions';
import { take } from 'rxjs/operators';

const SUPPORTED_CURRENCIES = ['EUR', 'USD', 'SEK', 'GBP', 'VND'] as const;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, NavbarComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly actions$ = inject(Actions);

  readonly accounts = toSignal(this.store.select(selectAllAccounts), { initialValue: [] });
  readonly loading = toSignal(this.store.select(selectAccountsLoading), { initialValue: false });
  readonly creating = toSignal(this.store.select(selectAccountsCreating), { initialValue: false });
  readonly error = toSignal(this.store.select(selectAccountsError), { initialValue: null });

  readonly skeletonItems = Array(6).fill(0);
  readonly currencies = SUPPORTED_CURRENCIES;

  // Modal state
  showCreateModal = signal(false);
  newAccountName = signal('');
  newAccountCurrency = signal<string>('EUR');
  createError = signal<string | null>(null);

  ngOnInit(): void {
    this.store.dispatch(loadAccounts());
  }

  openCreateModal(): void {
    this.newAccountName.set('');
    this.newAccountCurrency.set('EUR');
    this.createError.set(null);
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  submitCreateAccount(): void {
    const name = this.newAccountName().trim();
    if (!name) {
      this.createError.set('Account name is required.');
      return;
    }
    this.createError.set(null);
    this.store.dispatch(createAccount({ accountName: name, currency: this.newAccountCurrency() }));

    this.actions$.pipe(
      ofType(createAccountSuccess, createAccountFailure),
      take(1)
    ).subscribe((action) => {
      if (action.type === createAccountSuccess.type) {
        this.closeCreateModal();
      } else {
        this.createError.set((action as { error: string }).error);
      }
    });
  }

  navigateToAccount(account: AccountResponse): void {
    this.router.navigate(['/accounts', account.id]);
  }

  readonly CURRENCY_SYMBOLS: Record<string, string> = {
    EUR: '€', USD: '$', GBP: '£', SEK: 'kr', VND: '₫'
  };

  currencySymbol(code: string): string {
    return this.CURRENCY_SYMBOLS[code] ?? code;
  }

  formatBalance(balance: number, currency: string): string {
    const symbol = this.currencySymbol(currency);
    return `${currency} ${symbol}${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge-success';
      case 'CLOSED': return 'badge-danger';
      case 'BLOCKED': return 'badge-warning';
      case 'INACTIVE': return 'badge-info';
      default: return 'badge-info';
    }
  }
}
