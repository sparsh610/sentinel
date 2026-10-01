import { CurrencyPipe, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, EMPTY, interval, map, merge, startWith, Subject, switchMap } from 'rxjs';

import { InvestigationsService } from '../investigations/investigations.service';
import { Alert, AlertStatus, RULE_LABELS, Rule, SimulationReport } from './alert.models';
import { AlertsService } from './alerts.service';

/**
 * Polled, not pushed. Scoring raises a handful of alerts a minute at demo volume, and a five-
 * second poll is simpler to operate than a socket. The investigation page polls faster while the
 * agent is working, and stops once it has drafted.
 */
const POLL_INTERVAL_MS = 5_000;

const TABS: { status: AlertStatus; label: string }[] = [
  { status: 'OPEN', label: 'Open' },
  { status: 'IN_REVIEW', label: 'In review' },
  { status: 'ESCALATED', label: 'Escalated' },
  { status: 'CLOSED', label: 'Closed' },
];

@Component({
  selector: 'sentinel-alert-queue',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, LowerCasePipe],
  templateUrl: './alert-queue.html',
  styleUrl: './alert-queue.scss',
})
export class AlertQueue {

  private readonly alertsService = inject(AlertsService);
  private readonly investigations = inject(InvestigationsService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  /** Fires when the tab changes, so the new list does not wait for the next poll. */
  private readonly refresh = new Subject<void>();

  protected readonly tabs = TABS;
  protected readonly status = signal<AlertStatus>('OPEN');
  protected readonly alerts = signal<Alert[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expanded = signal<string | null>(null);
  protected readonly simulating = signal(false);
  protected readonly opening = signal<string | null>(null);
  protected readonly lastSimulation = signal<SimulationReport | null>(null);

  constructor() {
    merge(interval(POLL_INTERVAL_MS), this.refresh)
      .pipe(
        startWith(0),
        switchMap(() => this.alertsService.queue(this.status()).pipe(
          catchError((e: unknown) => {
            this.error.set(describe(e, 'scoring-service'));
            return EMPTY;
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(alerts => {
        this.alerts.set(alerts);
        this.loaded.set(true);
        this.error.set(null);
      });
  }

  protected label(rule: Rule): string {
    return RULE_LABELS[rule];
  }

  protected toggle(alert: Alert): void {
    this.expanded.update(current => (current === alert.id ? null : alert.id));
  }

  protected show(status: AlertStatus): void {
    this.status.set(status);
    this.expanded.set(null);
    this.loaded.set(false);
    this.refresh.next();
  }

  /**
   * Open or decided alerts start (or resume) the agent; decided ones open their latest
   * investigation. Either way the analyst lands on the investigation page.
   */
  protected investigate(alert: Alert, event: Event): void {
    event.stopPropagation();
    this.opening.set(alert.id);
    const decided = alert.status === 'ESCALATED' || alert.status === 'CLOSED';
    const target = decided
      ? this.investigations.forAlert(alert.id).pipe(map(list => list[0]))
      : this.investigations.start(alert.id);

    target.subscribe({
      next: investigation => {
        this.opening.set(null);
        if (investigation) {
          void this.router.navigate(['/investigations', investigation.id]);
        } else {
          this.error.set('This alert was decided without an investigation on record.');
        }
      },
      error: (e: unknown) => {
        this.opening.set(null);
        this.error.set(describe(e, 'copilot-service'));
      },
    });
  }

  protected simulate(): void {
    this.simulating.set(true);
    this.alertsService.simulate().subscribe({
      next: report => {
        this.lastSimulation.set(report);
        this.simulating.set(false);
      },
      error: (e: unknown) => {
        this.error.set(describe(e, 'tx-ingest'));
        this.simulating.set(false);
      },
    });
  }
}

/** Turns a failed request into something an analyst can act on. */
function describe(error: unknown, service: string): string {
  if (error instanceof HttpErrorResponse) {
    // The dev-server proxy answers 5xx itself when the target service is not running.
    if (error.status === 0 || error.status >= 502) {
      const kafka = service === 'copilot-service' ? '' : ', and is Kafka up (docker compose --profile stream up -d)';
      return `${service} is not reachable. Is it running${kafka}?`;
    }
    const problem = error.error as { detail?: string } | null;
    return problem?.detail ?? `${service} returned ${error.status}.`;
  }
  return 'Something went wrong.';
}
