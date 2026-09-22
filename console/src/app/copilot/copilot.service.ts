import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Citation, DocumentSummary } from './copilot.models';

/** One frame off the SSE stream. */
export type StreamEvent =
  | { kind: 'citations'; citations: Citation[]; grounded: boolean }
  | { kind: 'token'; text: string }
  | { kind: 'done' };

/** SSE framing: frames are separated by a blank line, fields by a single newline. */
const FRAME_SEPARATOR = '\n\n';
const LINE_SEPARATOR = '\n';

@Injectable({ providedIn: 'root' })
export class CopilotService {

  private readonly http = inject(HttpClient);

  documents(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>('/api/documents');
  }

  upload(file: File, classification: string, title: string): Observable<DocumentSummary> {
    const form = new FormData();
    form.append('file', file);
    form.append('classification', classification);
    if (title.trim()) {
      form.append('title', title.trim());
    }
    return this.http.post<DocumentSummary>('/api/documents', form);
  }

  /**
   * Streams an answer.
   *
   * Uses fetch rather than EventSource because EventSource cannot issue a POST, and the
   * question belongs in a body rather than a query string - questions are long, and putting
   * them in a URL would scatter them through access logs.
   */
  async *ask(question: string, signal: AbortSignal): AsyncGenerator<StreamEvent> {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question }),
      signal,
    });

    if (!response.ok) {
      throw new Error(await this.describeFailure(response));
    }
    if (!response.body) {
      throw new Error('The server returned no response body.');
    }

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';

    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += value;

        // A frame can arrive split across reads, so whatever follows the last separator
        // stays in the buffer until the rest of it turns up.
        const frames = buffer.split(FRAME_SEPARATOR);
        buffer = frames.pop() ?? '';

        for (const frame of frames) {
          const parsed = this.parseFrame(frame);
          if (parsed) {
            yield parsed;
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }

  private parseFrame(frame: string): StreamEvent | null {
    let event = 'message';
    const dataLines: string[] = [];

    for (const line of frame.split(LINE_SEPARATOR)) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        // Exactly one leading space is part of the SSE framing, not the payload.
        dataLines.push(line.slice(5).replace(/^ /, ''));
      }
    }

    const data = dataLines.join(LINE_SEPARATOR);

    switch (event) {
      case 'citations': {
        const payload = JSON.parse(data) as { citations: Citation[]; groundedInSources: boolean };
        return {
          kind: 'citations',
          citations: payload.citations,
          grounded: payload.groundedInSources,
        };
      }
      case 'token': {
        // JSON, not raw text: SSE framing would otherwise swallow a token's leading space
        // and weld words together in the rendered answer.
        const payload = JSON.parse(data) as { text: string };
        return { kind: 'token', text: payload.text };
      }
      case 'done':
        return { kind: 'done' };
      default:
        return null;
    }
  }

  /** Turns the service's RFC 7807 problem details into something worth showing a person. */
  private async describeFailure(response: Response): Promise<string> {
    try {
      const problem = await response.json() as { title?: string; detail?: string };
      return problem.detail ?? problem.title ?? `Request failed (${response.status})`;
    } catch {
      return `Request failed (${response.status})`;
    }
  }
}
