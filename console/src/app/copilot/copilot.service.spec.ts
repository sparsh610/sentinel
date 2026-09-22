import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CopilotService, StreamEvent } from './copilot.service';

/** Builds one SSE frame the way copilot-service writes it. */
function frame(event: string, data: unknown): string {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
}

/**
 * The SSE reader is the part of the frontend most likely to break subtly: a frame can arrive
 * split across two network reads, and getting that wrong drops tokens in a way that looks like
 * a model problem rather than a parsing one.
 */
describe('CopilotService stream parsing', () => {

  let service: CopilotService;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    service = TestBed.inject(CopilotService);
  });

  /** Feeds the given chunks through ask() as if they were network reads. */
  async function collect(chunks: string[]): Promise<StreamEvent[]> {
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        const encoder = new TextEncoder();
        for (const chunk of chunks) {
          controller.enqueue(encoder.encode(chunk));
        }
        controller.close();
      },
    });

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    );

    const events: StreamEvent[] = [];
    for await (const event of service.ask('q', new AbortController().signal)) {
      events.push(event);
    }
    return events;
  }

  function joinTokens(events: StreamEvent[]): string {
    return events
      .filter((e): e is Extract<StreamEvent, { kind: 'token' }> => e.kind === 'token')
      .map(e => e.text)
      .join('');
  }

  it('reads citations, tokens and done in order', async () => {
    const events = await collect([
      frame('citations', {
        citations: [{
          marker: 1,
          documentId: 'd',
          title: 'AML',
          chunkIndex: 0,
          score: 0.8,
          excerpt: 'text',
        }],
        groundedInSources: true,
      }),
      frame('token', { text: 'Report' }),
      frame('token', { text: ' within 24 hours' }),
      frame('done', ''),
    ]);

    expect(events.map(e => e.kind)).toEqual(['citations', 'token', 'token', 'done']);

    const citations = events[0] as Extract<StreamEvent, { kind: 'citations' }>;
    expect(citations.grounded).toBe(true);
    expect(citations.citations[0].title).toBe('AML');

    expect(joinTokens(events)).toBe('Report within 24 hours');
  });

  it('preserves a leading space in a token', async () => {
    // Regression: the answer used to render as "Accordingto,AML-04.3" because raw-text SSE
    // framing ate each token's leading space and welded the words together.
    const events = await collect([
      frame('token', { text: 'According' }),
      frame('token', { text: ' to' }),
      frame('token', { text: ' AML-04.3' }),
    ]);

    expect(joinTokens(events)).toBe('According to AML-04.3');
  });

  it('reassembles a frame split across two reads', async () => {
    const whole = frame('token', { text: 'half' });
    const cut = Math.floor(whole.length / 2);

    const events = await collect([whole.slice(0, cut), whole.slice(cut)]);

    expect(events).toHaveLength(1);
    expect((events[0] as Extract<StreamEvent, { kind: 'token' }>).text).toBe('half');
  });

  it('surfaces the problem detail when the request fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ detail: 'The language model endpoint is not reachable.' }),
        { status: 503 },
      ),
    );

    await expect(
      service.ask('q', new AbortController().signal).next(),
    ).rejects.toThrow(/not reachable/);
  });
});
