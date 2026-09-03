import { describe, expect, it } from 'vitest';
import { origin } from './a2a';

describe('origin', () => {
	it('names the answering agent the way the trace names it everywhere else', () => {
		const point = origin(
			{ status: { state: 'completed' } },
			{ name: 'antwort', parts: [{ kind: 'data', data: { source: 'atra.dent Beratungsagent' } }] }
		);

		expect(point.text).toBe('Antwort von Beratungsagent');
	});

	it('keeps the name from the wire in the data of the point', () => {
		const point = origin(
			{ status: { state: 'completed' } },
			{ name: 'antwort', parts: [{ kind: 'data', data: { source: 'atra.dent Beratungsagent' } }] }
		);

		expect(point.data?.source).toBe('atra.dent Beratungsagent');
	});

	it('names the Orchestrator where no agent answered', () => {
		const point = origin({ status: { state: 'completed' } }, undefined);

		expect(point.text).toBe('Antwort vom Orchestrator');
	});
});
