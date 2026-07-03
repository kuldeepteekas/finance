import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const credentials = sessionStorage.getItem('fin_creds');

  if (!credentials) {
    router.navigate(['/login']);
    return false;
  }

  return true;
};
