import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/home/home.component').then((m) => m.HomeComponent)
  },
  {
    path: 'accounts/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/account-overview/account-overview.component').then(
        (m) => m.AccountOverviewComponent
      )
  },
  {
    path: 'accounts/:accountId/transactions/:txId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/transaction-overview/transaction-overview.component').then(
        (m) => m.TransactionOverviewComponent
      )
  },
  {
    path: '**',
    redirectTo: ''
  }
];
