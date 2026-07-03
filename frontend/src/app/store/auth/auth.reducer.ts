import { createReducer, on } from '@ngrx/store';
import { login, loginSuccess, loginFailure, logout } from './auth.actions';

export interface AuthState {
  credentials: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export const initialAuthState: AuthState = {
  credentials: null,
  isAuthenticated: false,
  loading: false,
  error: null
};

export const authReducer = createReducer(
  initialAuthState,

  on(login, (state) => ({
    ...state,
    loading: true,
    error: null
  })),

  on(loginSuccess, (state, { credentials }) => ({
    ...state,
    credentials,
    isAuthenticated: true,
    loading: false,
    error: null
  })),

  on(loginFailure, (state, { error }) => ({
    ...state,
    credentials: null,
    isAuthenticated: false,
    loading: false,
    error
  })),

  on(logout, () => ({
    ...initialAuthState
  }))
);
