import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { App } from './app';
import { signedInAs } from './auth/auth.testing';

describe('App', () => {
  function render(...auth: Parameters<typeof signedInAs>) {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideRouter([]), signedInAs(...auth)],
    });
    return TestBed.createComponent(App);
  }

  it('renders the masthead and links both screens', () => {
    const fixture = render('alex.analyst', 'ANALYST');
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('h1')?.textContent).toContain('Sentinel');

    const links = Array.from(host.querySelectorAll('nav a')).map(a => a.getAttribute('href'));
    expect(links).toEqual(['/alerts', '/copilot']);
    expect(host.querySelector('router-outlet')).toBeTruthy();
    expect(host.querySelector('.user')?.textContent).toContain('alex.analyst');
  });

  it('shows the audit log only to a senior approver', () => {
    const fixture = render('sam.senior', 'ANALYST', 'SENIOR_APPROVER');
    fixture.detectChanges();

    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a'))
      .map(a => a.getAttribute('href'));
    expect(links).toEqual(['/alerts', '/copilot', '/audit']);
  });
});
