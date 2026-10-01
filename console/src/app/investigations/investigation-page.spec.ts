import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Investigation } from './investigation.models';
import { InvestigationPage } from './investigation-page';
import { InvestigationsService } from './investigations.service';

const DRAFTED: Investigation = {
  id: 'inv-1',
  alertId: 'a-1',
  transactionId: 't-1',
  customerId: 'C-20001',
  amount: 9800,
  currency: 'EUR',
  planner: 'FIXED',
  status: 'DRAFTED',
  caseNote: 'Summary: three cash deposits just under EUR 10,000 within 24 hours.',
  noteSource: 'MODEL',
  failureReason: null,
  startedAt: '2026-10-01T10:00:00Z',
  finishedAt: '2026-10-01T10:00:20Z',
  steps: [
    { stepNo: 1, tool: 'riskScore', input: '{"alertId":"a-1","requestedBy":"plan"}', output: '{}',
      status: 'OK', durationMs: 12, startedAt: '2026-10-01T10:00:00Z' },
    { stepNo: 2, tool: 'peerSegment', input: '{"requestedBy":"plan"}', output: 'Step limit of 6 reached',
      status: 'REFUSED', durationMs: 0, startedAt: '2026-10-01T10:00:01Z' },
  ],
  decisions: [],
};

function render(service: Partial<InvestigationsService>) {
  TestBed.configureTestingModule({
    imports: [InvestigationPage],
    providers: [provideRouter([]), { provide: InvestigationsService, useValue: service }],
  });
  const fixture = TestBed.createComponent(InvestigationPage);
  fixture.componentRef.setInput('id', 'inv-1');
  // The route id reaches the component through an effect; let it run, then render the result.
  TestBed.tick();
  fixture.detectChanges();
  return fixture;
}

describe('InvestigationPage', () => {

  it('shows the trace step by step, the drafted note, and who drafted it', () => {
    const fixture = render({ get: () => of(DRAFTED) });
    const host: HTMLElement = fixture.nativeElement;

    const steps = Array.from(host.querySelectorAll('.trace li'));
    expect(steps.map(s => s.querySelector('.tool')?.textContent)).toEqual(['Read the alert', 'Peer segment']);
    expect(steps[1].getAttribute('data-status')).toBe('REFUSED');
    expect(host.querySelector('.note')?.textContent).toContain('three cash deposits');
    expect(host.querySelector('.badge')?.textContent).toContain('local model');
  });

  it('records a decision only against a named person', () => {
    const decide = vi.fn(() => of({ ...DRAFTED, status: 'AWAITING_SIGN_OFF' } as Investigation));
    const fixture = render({ get: () => of(DRAFTED), decide });
    const host: HTMLElement = fixture.nativeElement;
    const escalate = host.querySelector('.decision .primary') as HTMLButtonElement;

    escalate.click();
    fixture.detectChanges();
    expect(decide).not.toHaveBeenCalled();
    expect(host.querySelector('.error')?.textContent).toContain('Enter your name');

    const name = host.querySelector('.decision input') as HTMLInputElement;
    name.value = 'Ana';
    name.dispatchEvent(new Event('input'));
    escalate.click();
    fixture.detectChanges();

    expect(decide).toHaveBeenCalledWith('inv-1', 'ESCALATE', 'Ana', '');
    expect(host.querySelector('.status')?.textContent).toContain('senior sign-off');
  });

  it('asks a senior approver, not the analyst, once the case is escalated', () => {
    const fixture = render({ get: () => of({ ...DRAFTED, status: 'AWAITING_SIGN_OFF' } as Investigation) });
    const host: HTMLElement = fixture.nativeElement;

    expect(host.querySelector('.decision h3')?.textContent).toContain('Senior approver');
    expect(host.querySelector('.decision .primary')?.textContent).toContain('Approve report');
  });
});
