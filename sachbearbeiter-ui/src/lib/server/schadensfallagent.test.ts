import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { environment } = vi.hoisted(() => ({
	environment: {} as Record<string, string | undefined>
}));
vi.mock('$env/dynamic/private', () => ({ env: environment }));

const { checkSchadensfall, DEFAULT_URL } = await import('./schadensfallagent');

function response(body: unknown, ok = true, status = 200) {
	return { ok, status, json: async () => body } as Response;
}

function completedTask(text: string, empfehlung: string) {
	return {
		jsonrpc: '2.0',
		id: '1',
		result: {
			kind: 'task',
			id: 'task-1',
			status: { state: 'completed' },
			artifacts: [
				{
					name: 'bewertung',
					parts: [
						{ kind: 'text', text },
						{
							kind: 'data',
							data: {
								bewertung: { empfehlung, erstattungsvorschlag: '842.00' },
								protokoll: [],
								schadensfallId: 50071
							}
						}
					]
				}
			]
		}
	};
}

function runningTask(state = 'working', progress?: string) {
	return response({
		jsonrpc: '2.0',
		id: '1',
		result: {
			kind: 'task',
			id: 'task-1',
			contextId: 'ctx-1',
			artifacts: [],
			status: progress
				? { state, message: { parts: [{ kind: 'text', text: progress }] } }
				: { state }
		}
	});
}

function rejection(text: string) {
	return response({
		jsonrpc: '2.0',
		id: '1',
		result: {
			kind: 'task',
			id: 'task-1',
			status: { state: 'rejected', message: { parts: [{ kind: 'text', text }] } }
		}
	});
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
	for (const key of Object.keys(environment)) delete environment[key];
	fetchMock = vi.fn();
	vi.stubGlobal('fetch', fetchMock);
	vi.spyOn(console, 'error').mockImplementation(() => {});
	vi.spyOn(console, 'info').mockImplementation(() => {});
});

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe('checkSchadensfall — what comes back', () => {
	it('returns outcome, state, prose and recommendation of a finished turn', async () => {
		fetchMock.mockResolvedValue(
			response(completedTask('Fall 50071: Freigabe empfohlen, Vorschlag 842.00 EUR.', 'freigabe'))
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result).toEqual({
			outcome: 'geprueft',
			status: 'completed',
			text: 'Fall 50071: Freigabe empfohlen, Vorschlag 842.00 EUR.',
			empfehlung: 'freigabe'
		});
	});

	it('passes the recommendation eskalation through unchanged', async () => {
		fetchMock.mockResolvedValue(
			response(
				completedTask(
					'Fall 50071: Eskalation an die Sachbearbeitung — Gründe: GOZ_UNCLEAR.',
					'eskalation'
				)
			)
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('geprueft');
		expect(result.empfehlung).toBe('eskalation');
	});

	it('reads the text of a rejection from status.message and not from an artifact', async () => {
		fetchMock.mockResolvedValue(rejection('Zu Fall 50071 kann ich Ihnen nichts sagen.'));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('abgelehnt');
		expect(result.status).toBe('rejected');
		expect(result.text).toContain('kann ich Ihnen nichts sagen');
		expect(result.empfehlung).toBeUndefined();
	});
});

describe('checkSchadensfall — what goes out', () => {
	beforeEach(() => {
		fetchMock.mockResolvedValue(
			response(completedTask('Fall 50071: Freigabe empfohlen.', 'freigabe'))
		);
	});

	it('sends to the default address and carries the Kundennummer as x-kunden-id', async () => {
		await checkSchadensfall(50071, 4711);

		const [requestUrl, init] = fetchMock.mock.calls[0];
		expect(requestUrl).toBe(DEFAULT_URL);
		expect(init.headers['x-kunden-id']).toBe('4711');
	});

	it('follows SCHADENSFALLAGENT_URL and adds the missing slash', async () => {
		environment.SCHADENSFALLAGENT_URL = 'http://agent.intern:9000';

		await checkSchadensfall(50071, 4711);

		expect(fetchMock.mock.calls[0][0]).toBe('http://agent.intern:9000/');
	});

	it('names the case number as a free-standing five-digit number and again as a data part', async () => {
		await checkSchadensfall(50071, 4711);

		const body = JSON.parse(fetchMock.mock.calls[0][1].body);
		expect(body.method).toBe('message/send');
		const [text, data] = body.params.message.parts;
		expect(text.text).toMatch(/(?<!\d)50071(?!\d)/);
		expect(data).toEqual({ kind: 'data', data: { schadensfallId: 50071 } });
	});
});

describe('checkSchadensfall — the situations that look alike', () => {
	it('where the agent is dead it says the Schadensfall stands on eingereicht — with a caveat', async () => {
		fetchMock.mockRejectedValue(new TypeError('fetch failed'));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('gestoert');
		expect(result.text).toContain('eingereicht');
		expect(result.text).toContain('SCHADENSFALL_POLL_AKTIV=false');
	});

	it('where the timeout runs out it does NOT say the Schadensfall stands on eingereicht', async () => {
		fetchMock.mockRejectedValue(new DOMException('The operation was aborted.', 'TimeoutError'));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('laeuft');
		expect(result.text).not.toContain('eingereicht');
		expect(result.text).toContain('läuft');
		expect(result.status).toBeUndefined();
	});

	it('working reads as a running Pruefung — the rule, not the exception', async () => {
		fetchMock.mockResolvedValue(runningTask());

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('laeuft');
		expect(result.status).toBe('working');
		expect(result.text).toContain('übernommen');
	});

	it('shows the progress the running task sends along', async () => {
		fetchMock.mockResolvedValue(runningTask('working', 'frage den Arztservice'));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.text).toBe('frage den Arztservice');
		expect(result.outcome).toBe('laeuft');
	});

	it('submitted reads as a running Pruefung too', async () => {
		fetchMock.mockResolvedValue(runningTask('submitted'));

		expect((await checkSchadensfall(50071, 4711)).outcome).toBe('laeuft');
	});

	it('does not widen the fallback: an unknown state stays abgelehnt', async () => {
		fetchMock.mockResolvedValue(runningTask('input-required', 'Welchen Fall soll ich prüfen?'));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('abgelehnt');
		expect(result.status).toBe('input-required');
	});

	it('a lost claim on in_pruefung reads as a running Pruefung', async () => {
		fetchMock.mockResolvedValue(
			rejection(
				'Fall 50071 ließ sich nicht übernehmen; er steht jetzt auf in_pruefung. ' +
					'Ein anderer war schneller.'
			)
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('laeuft');
		expect(result.status).toBe('rejected');
		expect(result.text).toContain('ließ sich nicht übernehmen');
	});

	it('a Schadensfall already standing on in_pruefung reads the same way', async () => {
		fetchMock.mockResolvedValue(
			rejection('Fall 50071 steht auf in_pruefung; geprüft werden nur eingereichte Fälle.')
		);

		expect((await checkSchadensfall(50071, 4711)).outcome).toBe('laeuft');
	});

	it('a lost claim on genehmigt does NOT read as a running Pruefung', async () => {
		fetchMock.mockResolvedValue(
			rejection(
				'Fall 50071 ließ sich nicht übernehmen; er steht jetzt auf genehmigt. ' +
					'Ein anderer war schneller.'
			)
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('weiterbearbeitet');
		expect(result.text).toContain('genehmigt');
	});

	it('a decided Schadensfall reads as weiterbearbeitet, not as running', async () => {
		fetchMock.mockResolvedValue(
			rejection('Fall 50071 steht auf ausgezahlt; geprüft werden nur eingereichte Fälle.')
		);

		expect((await checkSchadensfall(50071, 4711)).outcome).toBe('weiterbearbeitet');
	});

	it('a collision during the Pruefung reads as weiterbearbeitet', async () => {
		fetchMock.mockResolvedValue(
			rejection(
				'Fall 50071 wurde während der Prüfung weiterbearbeitet; die Bewertung gilt nicht mehr.'
			)
		);

		expect((await checkSchadensfall(50071, 4711)).outcome).toBe('weiterbearbeitet');
	});

	it('a rejection that names no status stays abgelehnt', async () => {
		fetchMock.mockResolvedValue(rejection('Zu Fall 50071 kann ich Ihnen nichts sagen.'));

		expect((await checkSchadensfall(50071, 4711)).outcome).toBe('abgelehnt');
	});
});

describe('checkSchadensfall — failures', () => {
	it('an HTTP rejection becomes a readable message', async () => {
		fetchMock.mockResolvedValue(response('kaputt', false, 500));

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('gestoert');
		expect(result.text).toContain('nicht geantwortet');
	});

	it('shows the wording of a JSON-RPC error that explains something', async () => {
		fetchMock.mockResolvedValue(
			response({
				jsonrpc: '2.0',
				id: '1',
				error: {
					code: -32600,
					message: 'Der Header x-kunden-id muss eine positive Kundennummer sein.'
				}
			})
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('gestoert');
		expect(result.text).toContain('x-kunden-id');
	});

	it('keeps quiet about „Internal error: null" and says something usable instead', async () => {
		fetchMock.mockResolvedValue(
			response({
				jsonrpc: '2.0',
				id: '1',
				error: { code: -32603, message: 'Internal error: null' }
			})
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.text).not.toContain('Internal error');
		expect(result.text).toContain('nicht geantwortet');
	});

	it('a response without text is a failure — with a readable sentence', async () => {
		fetchMock.mockResolvedValue(
			response({
				jsonrpc: '2.0',
				id: '1',
				result: { kind: 'task', status: { state: 'completed' } }
			})
		);

		const result = await checkSchadensfall(50071, 4711);

		expect(result.outcome).toBe('gestoert');
		expect(result.text).toContain('nicht geantwortet');
	});
});
