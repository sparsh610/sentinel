import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Auth } from './auth/auth';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(Auth);

  protected roleLabel(): string {
    if (this.auth.hasRole('SENIOR_APPROVER')) {
      return 'senior approver';
    }
    return this.auth.hasRole('ANALYST') ? 'analyst' : 'no Sentinel role';
  }
}
