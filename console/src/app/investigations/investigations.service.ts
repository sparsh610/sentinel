import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DecisionAction, Investigation } from './investigation.models';

/** copilot-service's investigation API. The dev proxy sends /api/investigations to :8081. */
@Injectable({ providedIn: 'root' })
export class InvestigationsService {

  private readonly http = inject(HttpClient);

  /** Starts the agent on an alert - or returns the investigation already in progress for it. */
  start(alertId: string): Observable<Investigation> {
    return this.http.post<Investigation>('/api/investigations', { alertId });
  }

  get(id: string): Observable<Investigation> {
    return this.http.get<Investigation>(`/api/investigations/${id}`);
  }

  /** Newest first. */
  forAlert(alertId: string): Observable<Investigation[]> {
    return this.http.get<Investigation[]>('/api/investigations', { params: new HttpParams().set('alertId', alertId) });
  }

  /** The analyst: ESCALATE or CLOSE. */
  decide(id: string, action: DecisionAction, actor: string, comment: string): Observable<Investigation> {
    return this.http.post<Investigation>(`/api/investigations/${id}/decision`, { action, actor, comment });
  }

  /** The senior approver: APPROVE or RETURN. */
  signOff(id: string, action: DecisionAction, actor: string, comment: string): Observable<Investigation> {
    return this.http.post<Investigation>(`/api/investigations/${id}/sign-off`, { action, actor, comment });
  }
}
