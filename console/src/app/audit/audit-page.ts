import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { Auth } from '../auth/auth';

/** Mirrors copilot-service's GET /api/audit. */
export interface AuditEvent {
  id: number;
  occurredAt: string;
  actor: string;
  channel: 'API' | 'MCP' | 'AGENT';
  action: string;
  target: string | null;
  outcome: 'OK' | 'DENIED' | 'FAILED';
  detail: string | null;
}

/**
 * The audit log, newest first: every decision, upload, policy question, MCP tool call, agent run
 * and refused request. Senior approvers only - the service refuses anyone else.
 */
@Component({
  selector: 'sentinel-audit-page',
  imports: [DatePipe],
  templateUrl: './audit-page.html',
  styleUrl: './audit-page.scss',
})
export class AuditPage {

  private readonly http = inject(HttpClient);
  protected readonly auth = inject(Auth);

  protected readonly events = signal<AuditEvent[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly actor = signal('');

  constructor() {
    if (this.auth.hasRole('SENIOR_APPROVER')) {
      this.load();
    }
  }

  protected setActor(event: Event): void {
    this.actor.set((event.target as HTMLInputElement).value.trim());
  }

  protected load(): void {
    let params = new HttpParams().set('limit', 200);
    if (this.actor()) {
      params = params.set('actor', this.actor());
    }
    this.http.get<AuditEvent[]>('/api/audit', { params }).subscribe({
      next: events => {
        this.events.set(events);
        this.error.set(null);
      },
      error: (e: HttpErrorResponse) => this.error.set(
        e.status === 403 ? 'The audit log is for senior approvers.' : `copilot-service returned ${e.status}.`),
    });
  }
}
