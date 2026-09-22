import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Citation, Exchange } from './copilot.models';
import { CopilotService } from './copilot.service';

@Component({
  selector: 'sentinel-chat',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class Chat {

  private readonly copilot = inject(CopilotService);

  protected readonly question = signal('');
  protected readonly exchanges = signal<Exchange[]>([]);
  protected readonly busy = signal(false);

  /** Citations of the most recent exchange - the source panel follows the latest answer. */
  protected readonly latestCitations = computed<Citation[]>(() => {
    const all = this.exchanges();
    return all.length === 0 ? [] : all[all.length - 1].citations;
  });

  protected readonly canAsk = computed(() => this.question().trim().length > 0 && !this.busy());

  private controller: AbortController | null = null;

  protected async ask(): Promise<void> {
    if (!this.canAsk()) {
      return;
    }

    const asked = this.question().trim();
    this.question.set('');
    this.busy.set(true);

    this.append({
      question: asked,
      answer: '',
      citations: [],
      grounded: true,
      streaming: true,
    });

    this.controller = new AbortController();

    try {
      for await (const event of this.copilot.ask(asked, this.controller.signal)) {
        switch (event.kind) {
          case 'citations':
            // Sources arrive before the first token, so they render while the answer is still
            // being written.
            this.updateLast(e => ({ ...e, citations: event.citations, grounded: event.grounded }));
            break;
          case 'token':
            this.updateLast(e => ({ ...e, answer: e.answer + event.text }));
            break;
          case 'done':
            this.updateLast(e => ({ ...e, streaming: false }));
            break;
        }
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Something went wrong.';
      this.updateLast(e => ({ ...e, streaming: false, error: message }));
    } finally {
      this.updateLast(e => ({ ...e, streaming: false }));
      this.busy.set(false);
      this.controller = null;
    }
  }

  protected stop(): void {
    this.controller?.abort();
  }

  protected onKeydown(event: KeyboardEvent): void {
    // Enter sends, Shift+Enter is a newline - a compliance question is often a paragraph.
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void this.ask();
    }
  }

  /**
   * Splits an answer on its [n] citation markers so the template can render them as chips.
   * Done here rather than with innerHTML: the answer is model output and must never be
   * interpolated as markup.
   */
  protected segments(answer: string): Array<{ text: string; marker: number | null }> {
    const parts: Array<{ text: string; marker: number | null }> = [];
    const pattern = /\[(\d+)]/g;
    let cursor = 0;
    let match: RegExpExecArray | null;

    while ((match = pattern.exec(answer)) !== null) {
      if (match.index > cursor) {
        parts.push({ text: answer.slice(cursor, match.index), marker: null });
      }
      parts.push({ text: match[0], marker: Number(match[1]) });
      cursor = match.index + match[0].length;
    }

    if (cursor < answer.length) {
      parts.push({ text: answer.slice(cursor), marker: null });
    }
    return parts;
  }

  private append(exchange: Exchange): void {
    this.exchanges.update(all => [...all, exchange]);
  }

  private updateLast(change: (exchange: Exchange) => Exchange): void {
    this.exchanges.update(all => {
      if (all.length === 0) {
        return all;
      }
      const copy = [...all];
      copy[copy.length - 1] = change(copy[copy.length - 1]);
      return copy;
    });
  }
}
