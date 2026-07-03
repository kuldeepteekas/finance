import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { loginSuccess } from './store/auth/auth.actions';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
  styles: []
})
export class AppComponent implements OnInit {
  private readonly store = inject(Store);

  ngOnInit(): void {
    const credentials = sessionStorage.getItem('fin_creds');
    if (credentials) {
      this.store.dispatch(loginSuccess({ credentials }));
    }
  }
}
