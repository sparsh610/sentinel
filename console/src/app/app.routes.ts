import { Routes } from '@angular/router';

import { AlertQueue } from './alerts/alert-queue';
import { Chat } from './copilot/chat';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'alerts' },
  { path: 'alerts', component: AlertQueue, title: 'Alerts · Sentinel' },
  { path: 'copilot', component: Chat, title: 'Copilot · Sentinel' },
  { path: '**', redirectTo: 'alerts' },
];
