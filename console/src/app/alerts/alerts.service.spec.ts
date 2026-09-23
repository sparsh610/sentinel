import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AlertsService } from './alerts.service';

/**
 * The URLs matter more than they look: the dev-server proxy routes /api/alerts to
 * scoring-service and /api/simulations to tx-ingest, so a wrong path lands on the wrong service.
 */
describe('AlertsService', () => {

  let service: AlertsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AlertsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks scoring-service for open alerts by default', () => {
    service.queue().subscribe();

    const request = http.expectOne(r => r.url === '/api/alerts');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('status')).toBe('OPEN');
    expect(request.request.params.get('limit')).toBe('100');
    request.flush([]);
  });

  it('starts a simulation on tx-ingest', () => {
    let planted: string[] = [];
    service.simulate(10).subscribe(report => (planted = report.planted));

    const request = http.expectOne(r => r.url === '/api/simulations');
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('ordinary')).toBe('10');
    request.flush({ submitted: 15, planted: ['Structuring'] });

    expect(planted).toEqual(['Structuring']);
  });
});
