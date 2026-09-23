import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('renders the masthead and links both screens', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('h1')?.textContent).toContain('Sentinel');

    const links = Array.from(host.querySelectorAll('nav a')).map(a => a.getAttribute('href'));
    expect(links).toEqual(['/alerts', '/copilot']);
    expect(host.querySelector('router-outlet')).toBeTruthy();
  });
});
