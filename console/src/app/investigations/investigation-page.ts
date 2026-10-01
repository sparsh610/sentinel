import { CurrencyPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, interval, startWith, switchMap, takeWhile } from 'rxjs';

import {
  DecisionAction,
  Investigation,
  InvestigationStep,
  STATUS_LABELS,
  TOOL_LABELS,
} from './investigation.models';
import { InvestigationsService } from './investigations.service';

/** How often to refresh while the agent is still working. Stops once it has drafted. */
const POLL_WHILE_RUNNING_MS = 1_500;

@Component({
  selector: 'sentinel-investigation-page',
  imports: [CurrencyPipe, DatePipe, LowerCasePipe, RouterLink],
  templateUrl: './investigation-page.html',
  styleUrl: './investigation-page.scss',
})
export class InvestigationPage {

  /** Bound from the route, /investigations/:id. */
  readonly id = input.required<string>();

  private readonly service = inject(InvestigationsService);
  private readonly router = inject(Router);

  protected readonly investigation = signal<Investigation | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly openStep = signal<number | null>(null);
  protected readonly actor = signal('');
  protected readonly comment = signal('');

  protected readonly status = computed(() => this.investigation()?.status);
  protected readonly statusLabel = computed(() => {
    const status = this.status();
    return status ? STATUS_LABELS[status] : '';
  });

  constructor() {
    toObservable(this.id)
      .pipe(
        switchMap(id => interval(POLL_WHILE_RUNNING_MS).pipe(
          startWith(0),
          switchMap(() => this.service.get(id).pipe(
            catchError((e: unknown) => {
              this.error.set(describe(e));
              return EMPTY;
            }),
          )),
          // Keep the final answer too: the poll that sees DRAFTED is the one that shows the note.
          takeWhile(investigation => investigation.status === 'RUNNING', true),
        )),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(investigation => {
        this.investigation.set(investigation);
        this.error.set(null);
      });
  }

  protected toolLabel(step: InvestigationStep): string {
    return TOOL_LABELS[step.tool] ?? step.tool;
  }

  /** Who asked for the step - the fixed plan, the model, or the plan filling in for the model. */
  protected requestedBy(step: InvestigationStep): string | null {
    try {
      return (JSON.parse(step.input) as { requestedBy?: string }).requestedBy ?? null;
    } catch {
      return null;
    }
  }

  protected toggleStep(step: InvestigationStep): void {
    this.openStep.update(open => (open === step.stepNo ? null : step.stepNo));
  }

  protected setActor(event: Event): void {
    this.actor.set((event.target as HTMLInputElement).value);
  }

  protected setComment(event: Event): void {
    this.comment.set((event.target as HTMLTextAreaElement).value);
  }

  protected decide(action: DecisionAction): void {
    const current = this.investigation();
    const actor = this.actor().trim();
    if (!current || !actor) {
      this.error.set('Enter your name first - every decision is recorded against a person.');
      return;
    }
    const call = current.status === 'AWAITING_SIGN_OFF'
      ? this.service.signOff(current.id, action, actor, this.comment())
      : this.service.decide(current.id, action, actor, this.comment());

    this.busy.set(true);
    call.subscribe({
      next: updated => {
        this.investigation.set(updated);
        this.comment.set('');
        this.error.set(null);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(describe(e));
        this.busy.set(false);
      },
    });
  }

  /** After a failed run: start a fresh one on the same alert. */
  protected runAgain(): void {
    const current = this.investigation();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.service.start(current.alertId).subscribe({
      next: fresh => {
        this.busy.set(false);
        void this.router.navigate(['/investigations', fresh.id]);
      },
      error: (e: unknown) => {
        this.error.set(describe(e));
        this.busy.set(false);
      },
    });
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || error.status === 504) {
      return 'copilot-service is not reachable. Is it running?';
    }
    const problem = error.error as { detail?: string } | null;
    return problem?.detail ?? `copilot-service returned ${error.status}.`;
  }
  return 'Something went wrong.';
}
