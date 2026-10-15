import { Routes } from '@angular/router';

import { AlertQueue } from './alerts/alert-queue';
import { AuditPage } from './audit/audit-page';
import { Chat } from './copilot/chat';
import { InvestigationPage } from './investigations/investigation-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'alerts' },
  { path: 'alerts', component: AlertQueue, title: 'Alerts · Sentinel' },
  { path: 'investigations/:id', component: InvestigationPage, title: 'Investigation · Sentinel' },
  { path: 'copilot', component: Chat, title: 'Copilot · Sentinel' },
  { path: 'audit', component: AuditPage, title: 'Audit log · Sentinel' },
  { path: '**', redirectTo: 'alerts' },
];
