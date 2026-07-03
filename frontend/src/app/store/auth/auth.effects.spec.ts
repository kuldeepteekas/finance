import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { ReplaySubject, of, throwError } from 'rxjs';
import { take } from 'rxjs/operators';

import { AuthEffects } from './auth.effects';
import { AccountApiService } from '../../core/services/account-api.service';
import { login, loginSuccess, loginFailure, logout } from './auth.actions';
import { AccountResponse } from '../../core/models/account.model';

const mockAccount: AccountResponse = {
  id: 'acc-1',
  accountNumber: '1000000001',
  accountName: 'Main',
  currency: 'EUR',
  balance: 0,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00'
};

describe('AuthEffects', () => {
  let actions$: ReplaySubject<Action>;
  let effects: AuthEffects;
  let apiService: jasmine.SpyObj<AccountApiService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    apiService = jasmine.createSpyObj('AccountApiService', ['getAccounts']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AuthEffects,
        provideMockActions(() => actions$),
        { provide: AccountApiService, useValue: apiService },
        { provide: Router, useValue: mockRouter }
      ]
    });

    effects = TestBed.inject(AuthEffects);

    // Ensure a clean sessionStorage before each test
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  // ─── login$ ────────────────────────────────────────────────────────────────

  it('login$: dispatches loginSuccess with base64 credentials on API success', (done) => {
    apiService.getAccounts.and.returnValue(of([mockAccount]));

    effects.login$.pipe(take(1)).subscribe((action) => {
      const encoded = btoa('alice:secret');
      expect(action).toEqual(loginSuccess({ credentials: encoded }));
      done();
    });

    actions$.next(login({ username: 'alice', password: 'secret' }));
  });

  it('login$: temporarily sets fin_creds in sessionStorage before API call', (done) => {
    apiService.getAccounts.and.callFake(() => {
      // At this point the interceptor-compatible key must already be set
      const stored = sessionStorage.getItem('fin_creds');
      expect(stored).toBe(btoa('alice:secret'));
      return of([mockAccount]);
    });

    effects.login$.pipe(take(1)).subscribe(() => done());
    actions$.next(login({ username: 'alice', password: 'secret' }));
  });

  it('login$: dispatches loginFailure with 401 message on auth error', (done) => {
    apiService.getAccounts.and.returnValue(throwError(() => ({ status: 401 })));

    effects.login$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loginFailure({ error: 'Invalid username or password' }));
      expect(sessionStorage.getItem('fin_creds')).toBeNull();
      done();
    });

    actions$.next(login({ username: 'alice', password: 'wrong' }));
  });

  it('login$: dispatches loginFailure with generic message on non-401 error', (done) => {
    apiService.getAccounts.and.returnValue(throwError(() => ({ status: 500, message: 'Server down' })));

    effects.login$.pipe(take(1)).subscribe((action) => {
      expect(action).toEqual(loginFailure({ error: 'Server down' }));
      done();
    });

    actions$.next(login({ username: 'alice', password: 'pass' }));
  });

  // ─── loginSuccess$ ─────────────────────────────────────────────────────────

  it('loginSuccess$: stores credentials in sessionStorage and navigates to /', (done) => {
    actions$.next(loginSuccess({ credentials: 'dXNlcjpwYXNz' }));

    effects.loginSuccess$.pipe(take(1)).subscribe(() => {
      expect(sessionStorage.getItem('fin_creds')).toBe('dXNlcjpwYXNz');
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/']);
      done();
    });
  });

  // ─── logout$ ───────────────────────────────────────────────────────────────

  it('logout$: clears sessionStorage and navigates to /login', (done) => {
    sessionStorage.setItem('fin_creds', 'some-value');

    actions$.next(logout());

    effects.logout$.pipe(take(1)).subscribe(() => {
      expect(sessionStorage.getItem('fin_creds')).toBeNull();
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/login']);
      done();
    });
  });
});
