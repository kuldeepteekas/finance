import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { authGuard } from './auth.guard';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule]
    });
    router = TestBed.inject(Router);
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should return false and navigate to /login when no credentials in sessionStorage', () => {
    const navigateSpy = spyOn(router, 'navigate');

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should return true when credentials exist in sessionStorage', () => {
    sessionStorage.setItem('fin_creds', 'dXNlcjpwYXNz');

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBeTrue();
  });

  it('should not navigate when credentials are present', () => {
    sessionStorage.setItem('fin_creds', 'dXNlcjpwYXNz');
    const navigateSpy = spyOn(router, 'navigate');

    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
