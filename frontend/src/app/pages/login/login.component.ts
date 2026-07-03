import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import { login } from '../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError, selectIsAuthenticated } from '../../store/auth/auth.selectors';

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

  readonly loading = toSignal(this.store.select(selectAuthLoading), { initialValue: false });
  readonly error = toSignal(this.store.select(selectAuthError), { initialValue: null });
  readonly isAuthenticated = toSignal(this.store.select(selectIsAuthenticated), { initialValue: false });

  loginForm: FormGroup = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  ngOnInit(): void {
    if (sessionStorage.getItem('fin_creds')) {
      this.router.navigate(['/']);
    }
  }

  onSubmit(): void {
    if (this.loginForm.valid && !this.loading()) {
      const { username, password } = this.loginForm.value;
      this.store.dispatch(login({ username, password }));
    } else {
      this.loginForm.markAllAsTouched();
    }
  }

  get usernameControl() {
    return this.loginForm.get('username');
  }

  get passwordControl() {
    return this.loginForm.get('password');
  }
}
