import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavbarComponent } from '../../shared/components/navbar/navbar.component';
import { loadAccounts } from '../../store/accounts/accounts.actions';
import { selectAllAccounts, selectAccountsLoading, selectAccountsError } from '../../store/accounts/accounts.selectors';
import { AccountResponse } from '../../core/models/account.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, NavbarComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  readonly accounts = toSignal(this.store.select(selectAllAccounts), { initialValue: [] });
  readonly loading = toSignal(this.store.select(selectAccountsLoading), { initialValue: false });
  readonly error = toSignal(this.store.select(selectAccountsError), { initialValue: null });

  readonly skeletonItems = Array(6).fill(0);

  ngOnInit(): void {
    this.store.dispatch(loadAccounts());
  }

  navigateToAccount(account: AccountResponse): void {
    this.router.navigate(['/accounts', account.id]);
  }

  formatBalance(balance: number, currency: string): string {
    return `${currency} ${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
