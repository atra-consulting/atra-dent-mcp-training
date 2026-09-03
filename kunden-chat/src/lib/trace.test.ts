import { describe, expect, it } from 'vitest';
import {
	displayName,
	displayParts,
	duration,
	levels,
	offset,
	panelFields,
	unpack,
	withElapsed
} from './trace';
import type { ChatUIMessage, Protocol, TracePoint } from './types/chat';

function point(
	protocol: Protocol,
	sender: string,
	operation: string,
	data?: Record<string, unknown>
): TracePoint {
	return { text: operation, label: operation, sender, protocol, operation, data };
}

function hop(sender: string, peer: string): TracePoint {
	return {
		text: 'sprung',
		label: 'sprung',
		sender,
		protocol: 'A2A',
		peer,
		operation: 'message/stream'
	};
}

function message(...parts: (TracePoint | string)[]): ChatUIMessage {
	return {
		id: 'm1',
		role: 'assistant',
		parts: parts.map((part) =>
			typeof part === 'string'
				? ({ type: 'text', text: part } as const)
				: ({ type: 'data-trace', data: part } as const)
		)
	} as ChatUIMessage;
}

describe('displayParts', () => {
	it('folds an MCP call and its result into one line', () => {
		const parts = displayParts(
			message(
				point('MCP', 'beratung', 'bedingungen_suchen', { call: 1, arguments: 'Wartezeit' }),
				point('MCP', 'beratung', 'bedingungen_suchen', { call: 1, result: '§ 4' })
			)
		);

		expect(parts).toHaveLength(1);
		expect(parts[0]).toMatchObject({ kind: 'trace', result: '§ 4' });
	});

	it('marks a failed call on the line as a failure, not as a result', () => {
		const parts = displayParts(
			message(
				point('MCP', 'schadensfall', 'bewertung_abgeben', { call: 1, arguments: 'freigabe' }),
				point('MCP', 'schadensfall', 'bewertung_abgeben', {
					call: 1,
					failed: 'unbekannte Empfehlung: vielleicht'
				})
			)
		);

		expect(parts).toHaveLength(1);
		expect(parts[0]).toMatchObject({ failed: 'unbekannte Empfehlung: vielleicht' });
		expect((parts[0] as { result?: unknown }).result).toBeUndefined();
	});

	it('pairs a failed call only once', () => {
		const parts = displayParts(
			message(
				point('MCP', 'schadensfall', 'goz_pruefen', { call: 1 }),
				point('MCP', 'schadensfall', 'goz_pruefen', { call: 1, failed: 'kaputt' }),
				point('MCP', 'schadensfall', 'goz_pruefen', { call: 1, result: 'spaet' })
			)
		);

		expect(parts).toHaveLength(2);
		expect(parts[0]).toMatchObject({ failed: 'kaputt' });
		expect((parts[0] as { result?: unknown }).result).toBeUndefined();
	});

	it('tells the same tool called twice apart by the call number', () => {
		const parts = displayParts(
			message(
				point('MCP', 'beratung', 'tarif_lesen', { call: 1 }),
				point('MCP', 'beratung', 'tarif_lesen', { call: 2 }),
				point('MCP', 'beratung', 'tarif_lesen', { call: 2, result: 'zwei' }),
				point('MCP', 'beratung', 'tarif_lesen', { call: 1, result: 'eins' })
			)
		);

		expect(parts).toHaveLength(2);
		expect(parts[0]).toMatchObject({ result: 'eins' });
		expect(parts[1]).toMatchObject({ result: 'zwei' });
	});

	it('delivers the result of einordnen across the work in between', () => {
		const parts = displayParts(
			message(
				point('MODELL', 'orchestrator', 'einordnen', { call: 1, message: 'Welcher Tarif?' }),
				point('A2A', 'orchestrator', 'message/stream', {}),
				point('MCP', 'beratung', 'tarif_lesen', { call: 1 }),
				point('MCP', 'beratung', 'tarif_lesen', { call: 1, result: 'brillant' }),
				point('MODELL', 'orchestrator', 'einordnen', { call: 1, result: 'Beratungsagent' })
			)
		);

		expect(parts).toHaveLength(3);
		expect(parts[0]).toMatchObject({
			kind: 'trace',
			result: 'Beratungsagent'
		});
		expect((parts[0] as { trace: TracePoint }).trace.operation).toBe('einordnen');
	});

	it('does not pair A2A hops -- they have their own closing point', () => {
		const parts = displayParts(
			message(
				point('A2A', 'orchestrator', 'message/stream', { call: 1 }),
				point('A2A', 'orchestrator', 'message/stream', { call: 1, result: 'fertig' })
			)
		);

		expect(parts).toHaveLength(2);
	});

	it('does not pair across service boundaries', () => {
		const parts = displayParts(
			message(
				point('MCP', 'beratung', 'tarif_lesen', { call: 1 }),
				point('MCP', 'schadensfall', 'tarif_lesen', { call: 1, result: 'fremd' })
			)
		);

		expect(parts).toHaveLength(2);
	});

	it('leaves text where it stands', () => {
		const parts = displayParts(
			message(point('MODELL', 'orchestrator', 'einordnen', { call: 1 }), 'Die Antwort.')
		);

		expect(parts.map((part) => part.kind)).toEqual(['trace', 'text']);
	});

	it('keeps the time of the result point on an uninterrupted pairing', () => {
		const call = point('MCP', 'beratung', 'tarif_lesen', { call: 1 });
		call.timestamp = '2026-08-13T10:00:00.000Z';
		const back = point('MCP', 'beratung', 'tarif_lesen', { call: 1, result: 'brillant' });
		back.timestamp = '2026-08-13T10:00:00.040Z';

		const parts = displayParts(message(call, back));

		expect(parts[0]).toMatchObject({ resultTime: '2026-08-13T10:00:00.040Z' });
	});

	it('shows no result time where work lay between call and result', () => {
		const call = point('MODELL', 'orchestrator', 'einordnen', { call: 1 });
		call.timestamp = '2026-08-13T10:00:00.000Z';
		const between = point('MCP', 'beratung', 'tarif_lesen', { call: 1 });
		between.timestamp = '2026-08-13T10:00:00.500Z';
		const betweenResult = point('MCP', 'beratung', 'tarif_lesen', {
			call: 1,
			result: 'brillant'
		});
		betweenResult.timestamp = '2026-08-13T10:00:00.540Z';
		const back = point('MODELL', 'orchestrator', 'einordnen', {
			call: 1,
			result: 'Beratungsagent'
		});
		back.timestamp = '2026-08-13T10:00:14.600Z';

		const parts = displayParts(
			message(
				call,
				hop('orchestrator', 'beratung'),
				between,
				betweenResult,
				back
			)
		);

		expect(parts[0]).toMatchObject({ result: 'Beratungsagent' });
		expect((parts[0] as { resultTime?: string }).resultTime).toBeUndefined();
	});

	it('does not count text between call and result as a gap', () => {
		const call = point('MCP', 'beratung', 'tarif_lesen', { call: 1 });
		call.timestamp = '2026-08-13T10:00:00.000Z';
		const back = point('MCP', 'beratung', 'tarif_lesen', { call: 1, result: 'brillant' });
		back.timestamp = '2026-08-13T10:00:00.040Z';

		const parts = displayParts(message(call, 'Zwischentext.', back));

		expect(parts[0]).toMatchObject({ resultTime: '2026-08-13T10:00:00.040Z' });
	});

	it('marks the level on every trace part', () => {
		const parts = displayParts(
			message(
				hop('chat-ui', 'orchestrator'),
				point('MODELL', 'orchestrator', 'einordnen'),
				'Die Antwort.'
			)
		);

		expect(parts[0]).toMatchObject({ kind: 'trace', level: 0, returnHop: false });
		expect(parts[1]).toMatchObject({ kind: 'trace', level: 1 });
		expect(parts[2]).toMatchObject({ kind: 'text' });
	});
});

describe('offset and duration', () => {
	const start = '2026-08-13T10:00:00.000Z';

	it('counts the offset from the first point of the message', () => {
		expect(offset(start, start)).toBe('0 ms');
		expect(offset('2026-08-13T10:00:01.300Z', start)).toBe('1,3 s');
	});

	it('writes milliseconds below one second', () => {
		expect(duration(start, '2026-08-13T10:00:00.040Z')).toBe('40 ms');
	});

	it('writes seconds with one decimal place from one second up', () => {
		expect(duration(start, '2026-08-13T10:00:01.140Z')).toBe('1,1 s');
	});

	it('does not guess when one of the two times is missing or unreadable', () => {
		expect(offset(undefined, start)).toBeUndefined();
		expect(offset(start, undefined)).toBeUndefined();
		expect(duration(start, 'gestern')).toBeUndefined();
	});

	it('does not append a double sign on a negative offset', () => {
		expect(offset('2026-08-13T09:59:58.700Z', start)).toBe('-1,3 s');
	});
});

describe('levels', () => {
	it('stays flat as long as nobody hops', () => {
		const depths = levels([
			point('MCP', 'beratung', 'tarif_lesen'),
			point('MCP', 'beratung', 'bedingungen_suchen'),
			point('MODELL', 'beratung', 'antworten')
		]);

		expect(depths.map((e) => e.depth)).toEqual([0, 0, 0]);
		expect(depths.some((e) => e.returnHop)).toBe(false);
	});

	it('opens a level with every hop and folds back on the return hop', () => {
		const depths = levels([
			hop('chat-ui', 'orchestrator'),
			point('MODELL', 'orchestrator', 'einordnen'),
			hop('orchestrator', 'beratung'),
			point('MCP', 'beratung', 'tarif_lesen'),
			hop('beratung', 'orchestrator'),
			hop('orchestrator', 'chat-ui')
		]);

		expect(depths.map((e) => e.depth)).toEqual([0, 1, 1, 2, 2, 1]);
		expect(depths.map((e) => e.returnHop)).toEqual([false, false, false, false, true, true]);
	});

	it('inherits the current depth when a point names no sender', () => {
		const withoutSender: TracePoint = { text: 'sehe nach', protocol: 'INTERNAL' };

		const depths = levels([hop('chat-ui', 'orchestrator'), withoutSender]);

		expect(depths.map((e) => e.depth)).toEqual([0, 1]);
	});

	it('starts at zero when the first point names no sender', () => {
		const depths = levels([{ text: 'sehe nach', protocol: 'INTERNAL' }]);

		expect(depths.map((e) => e.depth)).toEqual([0]);
	});

	it('keeps the calls of a sub-agent one level under the hop that opened it', () => {
		const depths = levels([
			hop('chat-ui', 'orchestrator'),
			hop('orchestrator', 'atra.dent Beratungsagent'),
			point('MCP', 'beratung', 'mein_vertrag_lesen'),
			hop('atra.dent Beratungsagent', 'orchestrator')
		]);

		expect(depths.map((e) => e.depth)).toEqual([0, 1, 2, 2]);
		expect(depths.map((e) => e.returnHop)).toEqual([false, false, false, true]);
	});

	it('gives an unannounced service a level of its own', () => {
		const depths = levels([
			point('MODELL', 'orchestrator', 'einordnen'),
			point('MCP', 'beratung', 'tarif_lesen')
		]);

		expect(depths.map((e) => e.depth)).toEqual([0, 1]);
	});
});

describe('displayName', () => {
	it('names an agent by its id', () => {
		expect(displayName('beratung')).toBe('Beratungsagent');
	});

	it('gives the card name of an agent the same name as its id', () => {
		expect(displayName('atra.dent Beratungsagent')).toBe('Beratungsagent');
		expect(displayName('atra.dent Schadensfallagent')).toBe('Schadensfallagent');
	});

	it('leaves a name it does not know as it stands', () => {
		expect(displayName('gemini-3.6-flash')).toBe('gemini-3.6-flash');
	});
});

describe('withElapsed', () => {
	it('separates the duration from the size of the result', () => {
		expect(withElapsed('55 Zeilen, 6.110 Zeichen', '108 ms')).toBe(
			'55 Zeilen, 6.110 Zeichen · 108 ms'
		);
	});

	it('leaves the line as it stands where no duration is known', () => {
		expect(withElapsed('24 Zeichen', undefined)).toBe('24 Zeichen');
	});
});

describe('unpack', () => {
	it('parses JSON that came along as a string', () => {
		expect(unpack('{"tarifId":"ATRA_DENT_B"}')).toEqual({ tarifId: 'ATRA_DENT_B' });
	});

	it('leaves a plain string as it stands', () => {
		expect(unpack('schlage in den Bedingungen nach')).toBe('schlage in den Bedingungen nach');
	});

	it('leaves a string that only looks like JSON as it stands', () => {
		expect(unpack('{kaputt')).toBe('{kaputt');
	});

	it('takes the content out of the envelope the MCP server wraps it in', () => {
		expect(unpack('[{"text":"{\\"monatsbeitrag\\":\\"20.90\\"}"}]')).toEqual({
			monatsbeitrag: '20.90'
		});
	});

	it('keeps an envelope that carries more than one part', () => {
		expect(unpack('[{"text":"eins"},{"text":"zwei"}]')).toEqual([
			{ text: 'eins' },
			{ text: 'zwei' }
		]);
	});

	it('keeps a part that carries more than its text', () => {
		expect(unpack('[{"text":"eins","mimeType":"text/plain"}]')).toEqual([
			{ text: 'eins', mimeType: 'text/plain' }
		]);
	});
});

describe('panelFields', () => {
	const point: TracePoint = {
		text: 'sehe in Ihrem Vertrag nach',
		protocol: 'MCP',
		data: { 'header x-kunden-id': 10001, call: 2, arguments: '{}' }
	};

	it('puts what came back before what was asked, and the transport last', () => {
		const fields = panelFields(point, '{"monatsbeitrag":"20.90"}', undefined);

		expect(fields.map(([field]) => field)).toEqual([
			'result',
			'arguments',
			'header x-kunden-id'
		]);
	});

	it('puts a failure where the result would have stood', () => {
		const fields = panelFields(point, undefined, 'kaputt');

		expect(fields.map(([field]) => field)).toEqual(['failed', 'arguments', 'header x-kunden-id']);
	});

	it('leaves out the call number the line is paired by', () => {
		const fields = panelFields(point, undefined, undefined);

		expect(fields.map(([field]) => field)).not.toContain('call');
	});

	it('carries the values along untouched', () => {
		const fields = panelFields(point, 'zurueck', undefined);

		expect(Object.fromEntries(fields)).toMatchObject({
			result: 'zurueck',
			'header x-kunden-id': 10001
		});
	});
});
