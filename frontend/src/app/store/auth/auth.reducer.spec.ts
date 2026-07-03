import { authReducer, initialAuthState, AuthState } from './auth.reducer';
import { login, loginSuccess, loginFailure, logout } from './auth.actions';

describe('authReducer', () => {
  it('should return initial state for unknown action', () => {
    const state = authReducer(undefined, { type: '@@UNKNOWN' } as any);
    expect(state).toEqual(initialAuthState);
  });

  // ─── login ─────────────────────────────────────────────────────────────────

  it('login: sets loading=true, clears error', () => {
    const prev: AuthState = { ...initialAuthState, error: 'previous error' };
    const state = authReducer(prev, login({ username: 'alice', password: 'pass' }));
    expect(state.loading).toBeTrue();
    expect(state.error).toBeNull();
    expect(state.isAuthenticated).toBeFalse();
  });

  // ─── loginSuccess ──────────────────────────────────────────────────────────

  it('loginSuccess: sets credentials, isAuthenticated=true, clears loading', () => {
    const prev: AuthState = { ...initialAuthState, loading: true };
    const state = authReducer(prev, loginSuccess({ credentials: 'dXNlcjpwYXNz' }));
    expect(state.credentials).toBe('dXNlcjpwYXNz');
    expect(state.isAuthenticated).toBeTrue();
    expect(state.loading).toBeFalse();
    expect(state.error).toBeNull();
  });

  // ─── loginFailure ──────────────────────────────────────────────────────────

  it('loginFailure: clears credentials, sets error, clears loading and isAuthenticated', () => {
    const prev: AuthState = {
      credentials: 'old-creds',
      isAuthenticated: true,
      loading: true,
      error: null
    };
    const state = authReducer(prev, loginFailure({ error: 'Invalid credentials' }));
    expect(state.credentials).toBeNull();
    expect(state.isAuthenticated).toBeFalse();
    expect(state.loading).toBeFalse();
    expect(state.error).toBe('Invalid credentials');
  });

  it('loginFailure: preserves other state fields while setting error', () => {
    const prev: AuthState = { ...initialAuthState, loading: true };
    const state = authReducer(prev, loginFailure({ error: 'Network error' }));
    expect(state.error).toBe('Network error');
    expect(state.credentials).toBeNull();
  });

  // ─── logout ────────────────────────────────────────────────────────────────

  it('logout: resets entire state to initial', () => {
    const loggedIn: AuthState = {
      credentials: 'dXNlcjpwYXNz',
      isAuthenticated: true,
      loading: false,
      error: null
    };
    const state = authReducer(loggedIn, logout());
    expect(state).toEqual(initialAuthState);
  });
});
