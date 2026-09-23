import { CurrencyPipe, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, interval, startWith, switchMap } from 'rxjs';

import { Alert, RULE_LABELS, Rule, SimulationReport } from './alert.models';
import { AlertsService } from './alerts.service';

/**
 * Polled, not pushed. Scoring raises a handful of alerts a minute at demo volume, and a five-
 * second poll is simpler to operate than a socket. The case view in weeks 5-6 streams over SSE
 * because there latency is the point; here it is not.
 */
const POLL_INTERVAL_MS = 5_000;

@Component({
  selector: 'sentinel-alert-queue',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, LowerCasePipe],
  templateUrl: './alert-queue.html',
  styleUrl: './alert-queue.scss',
})
export class AlertQueue {

  private readonly alertsService = inject(AlertsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly alerts = signal<Alert[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expanded = signal<string | null>(null);
  protected readonly simulating = signal(false);
  protected readonly lastSimulation = signal<SimulationReport | null>(null);

  constructor() {
    interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.alertsService.queue().pipe(
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
      return `${service} is not reachable. Is it running, and is Kafka up (docker compose --profile stream up -d)?`;
    }
    const problem = error.error as { detail?: string } | null;
    return problem?.detail ?? `${service} returned ${error.status}.`;
  }
  return 'Something went wrong.';
}
