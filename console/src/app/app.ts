import { Component } from '@angular/core';

import { Chat } from './copilot/chat';

@Component({
  imports: [Chat],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
}
