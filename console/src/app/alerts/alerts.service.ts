import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Alert, AlertStatus, SimulationReport } from './alert.models';

@Injectable({ providedIn: 'root' })
export class AlertsService {

  private readonly http = inject(HttpClient);

  /** The work queue, most serious first. Served by scoring-service. */
  queue(status: AlertStatus = 'OPEN', limit = 100): Observable<Alert[]> {
    const params = new HttpParams().set('status', status).set('limit', limit);
    return this.http.get<Alert[]>('/api/alerts', { params });
  }

  /** Pushes synthetic traffic through tx-ingest, with three known typologies planted in it. */
  simulate(ordinary = 40): Observable<SimulationReport> {
    const params = new HttpParams().set('ordinary', ordinary);
    return this.http.post<SimulationReport>('/api/simulations', null, { params });
  }
}
