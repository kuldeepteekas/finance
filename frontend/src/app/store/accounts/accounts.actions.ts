import { createAction, props } from '@ngrx/store';
import { AccountResponse } from '../../core/models/account.model';

export const loadAccounts = createAction('[Accounts] Load Accounts');

export const loadAccountsSuccess = createAction(
  '[Accounts] Load Accounts Success',
  props<{ accounts: AccountResponse[] }>()
);

export const loadAccountsFailure = createAction(
  '[Accounts] Load Accounts Failure',
  props<{ error: string }>()
);

export const loadAccount = createAction(
  '[Accounts] Load Account',
  props<{ id: string }>()
);

export const loadAccountSuccess = createAction(
  '[Accounts] Load Account Success',
  props<{ account: AccountResponse }>()
);

export const loadAccountFailure = createAction(
  '[Accounts] Load Account Failure',
  props<{ error: string }>()
);
