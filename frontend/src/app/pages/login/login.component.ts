import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import { login } from '../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError, selectIsAuthenticated } from '../../store/auth/auth.selectors';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  readonly loading = toSignal(this.store.select(selectAuthLoading), { initialValue: false });
  readonly error = toSignal(this.store.select(selectAuthError), { initialValue: null });
  readonly isAuthenticated = toSignal(this.store.select(selectIsAuthenticated), { initialValue: false });

  // Tab state
  activeTab = signal<'login' | 'register'>('login');
  registerLoading = signal(false);
  registerError = signal<string | null>(null);
  registerSuccess = signal<string | null>(null);

  loginForm: FormGroup = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  registerForm: FormGroup = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    fullName: [''],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  ngOnInit(): void {
    if (sessionStorage.getItem('fin_creds')) {
      this.router.navigate(['/']);
    }
  }

  switchTab(tab: 'login' | 'register'): void {
    this.activeTab.set(tab);
    this.registerError.set(null);
    this.registerSuccess.set(null);
  }

  onSubmit(): void {
    if (this.loginForm.valid && !this.loading()) {
      const { username, password } = this.loginForm.value;
      this.store.dispatch(login({ username, password }));
    } else {
      this.loginForm.markAllAsTouched();
    }
  }

  onRegister(): void {
    if (this.registerForm.invalid || this.registerLoading()) {
      this.registerForm.markAllAsTouched();
      return;
    }
    this.registerLoading.set(true);
    this.registerError.set(null);
    this.registerSuccess.set(null);

    const { username, email, fullName, password } = this.registerForm.value;
    this.http.post(`${environment.apiUrl}/users/register`, { username, email, fullName, password })
      .subscribe({
        next: () => {
          this.registerLoading.set(false);
          this.registerSuccess.set(`Account created! You can now sign in as "${username}".`);
          this.registerForm.reset();
          setTimeout(() => {
            this.loginForm.patchValue({ username });
            this.switchTab('login');
          }, 1500);
        },
        error: (err) => {
          this.registerLoading.set(false);
          const msg =
            err?.error?.error?.message ||
            err?.error?.message ||
            err?.message ||
            'Registration failed. Please try again.';
          this.registerError.set(msg);
        }
      });
  }

  get usernameControl() { return this.loginForm.get('username'); }
  get passwordControl() { return this.loginForm.get('password'); }

  get regUsername() { return this.registerForm.get('username'); }
  get regEmail()    { return this.registerForm.get('email'); }
  get regPassword() { return this.registerForm.get('password'); }
}
