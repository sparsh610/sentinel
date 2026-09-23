import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { Alert } from './alert.models';
import { AlertQueue } from './alert-queue';
import { AlertsService } from './alerts.service';

const STRUCTURING: Alert = {
  id: 'a-1',
  transactionId: 't-1',
  customerId: 'C-20003',
  customerSegment: 'BUSINESS',
  direction: 'CREDIT',
  channel: 'CASH',
  amount: 9812.5,
  currency: 'EUR',
  counterpartyName: null,
  counterpartyCountry: null,
  bookedAt: '2026-09-23T09:00:00Z',
  score: 0.85,
  status: 'OPEN',
  raisedAt: '2026-09-23T09:00:02Z',
  findings: [{
    rule: 'STRUCTURING',
    score: 0.85,
    reason: '3 cash deposits between 9000 and 10000 EUR within 24 hours',
  }],
};

function render(queue: AlertsService['queue']) {
  TestBed.configureTestingModule({
    imports: [AlertQueue],
    providers: [{ provide: AlertsService, useValue: { queue, simulate: () => of() } }],
  });
  const fixture = TestBed.createComponent(AlertQueue);
  fixture.detectChanges();
  return fixture;
}

describe('AlertQueue', () => {

  it('lists alerts with a readable rule name and shows the reason on demand', () => {
    const fixture = render(() => of([STRUCTURING]));
    const host: HTMLElement = fixture.nativeElement;

    expect(host.querySelector('.count')?.textContent?.trim()).toBe('1');
    expect(host.querySelector('.rule')?.textContent).toBe('Structuring');
    expect(host.querySelector('.detail')).toBeNull();

    (host.querySelector('tr.row') as HTMLElement).click();
    fixture.detectChanges();

    // The reason is what the analyst acts on; the score alone is not enough.
    expect(host.querySelector('.detail')?.textContent).toContain('3 cash deposits');
  });

  it('says how to get alerts when there are none', () => {
    const fixture = render(() => of([]));

    expect(fixture.nativeElement.querySelector('.empty')?.textContent).toContain('Simulate traffic');
  });

  it('names the service that is down rather than showing an empty queue', () => {
    const fixture = render(() => throwError(() => new HttpErrorResponse({ status: 504 })));
    const host: HTMLElement = fixture.nativeElement;

    expect(host.querySelector('.error')?.textContent).toContain('scoring-service is not reachable');
    expect(host.querySelector('.empty')?.textContent).not.toContain('No open alerts');
  });
});
