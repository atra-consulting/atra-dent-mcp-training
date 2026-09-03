import { randomUUID } from 'node:crypto';
import { env } from '$env/dynamic/private';
import type { Bewertungsempfehlung, Schadensfallstatus } from '$lib/types/api';
import type { Outcome, PruefungResponse } from '$lib/types/schadensfallagent';

export const DEFAULT_URL = 'http://localhost:8087/';

const KUNDEN_HEADER = 'x-kunden-id';

const METHOD = 'message/send';

const SCHADENSFALL_ID_KEY = 'schadensfallId';

const TIMEOUT_MS = 90_000;
const TIMEOUT_SECONDS = TIMEOUT_MS / 1000;

const COMPLETED = 'completed';
const REJECTED = 'rejected';
const FAILED = 'failed';
const SUBMITTED = 'submitted';
const WORKING = 'working';

const INTERNAL_ERROR = -32603;

const TIMED_OUT =
	`Der Schadensfallagent hat innerhalb von ${TIMEOUT_SECONDS} Sekunden nicht geantwortet — ` +
	'die Prüfung läuft bei ihm weiter und schreibt ihr Ergebnis in die Akte.';

const TAKEN_OVER = 'Der Schadensfallagent hat den Fall übernommen und prüft ihn gerade.';

const UNREACHABLE =
	'Der Schadensfallagent hat nicht geantwortet. Der Fall steht auf eingereicht: Läuft der ' +
	'Fallpoller, nimmt der Agent ihn beim nächsten Durchlauf von selbst auf — ist der Takt ' +
	'abgeschaltet (SCHADENSFALL_POLL_AKTIV=false), bleibt er liegen, bis jemand erneut drückt.';

const KNOWN_STATES: Schadensfallstatus[] = [
	'in_pruefung',
	'geprueft_freigabe',
	'geprueft_eskalation',
	'genehmigt',
	'abgelehnt',
	'ausgezahlt'
];

const CONTINUED_PHRASES = ['ließ sich nicht übernehmen', 'weiterbearbeitet'];

interface Part {
	kind?: string;
	text?: string;
	data?: unknown;
}

interface Artifact {
	name?: string;
	parts?: Part[];
}

interface Task {
	kind?: string;
	status?: { state?: string; message?: { parts?: Part[] } };
	artifacts?: Artifact[];
	parts?: Part[];
}

interface JsonRpcResponse {
	result?: Task;
	error?: { code?: number; message?: string };
}

export async function checkSchadensfall(id: number, kundenId: number): Promise<PruefungResponse> {
	try {
		return await send(id, kundenId);
	} catch (cause) {
		if (timedOut(cause)) {
			console.info(
				`Zuruf an den Schadensfallagenten nach ${TIMEOUT_SECONDS} s abgebrochen; ` +
					`Fall ${id} wird dort weiter geprüft`
			);
			return { outcome: 'laeuft', text: TIMED_OUT };
		}
		console.error('A2A-Aufruf am Schadensfallagenten fehlgeschlagen:', cause);
		return { outcome: 'gestoert', text: UNREACHABLE };
	}
}

function timedOut(cause: unknown): boolean {
	return cause instanceof DOMException && cause.name === 'TimeoutError';
}

async function send(id: number, kundenId: number): Promise<PruefungResponse> {
	const response = await fetch(url(), {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			Accept: 'application/json',
			[KUNDEN_HEADER]: String(kundenId)
		},
		body: JSON.stringify(request(id)),
		signal: AbortSignal.timeout(TIMEOUT_MS)
	});

	if (!response.ok) {
		console.error(`Schadensfallagent antwortete mit HTTP ${response.status} statt 200`);
		return { outcome: 'gestoert', text: UNREACHABLE };
	}

	const payload = (await response.json()) as JsonRpcResponse;
	if (payload.error) {
		return error(payload.error);
	}

	const result = payload.result;
	const artifact = result?.artifacts?.[0];
	const text =
		textOf(artifact?.parts) || textOf(result?.status?.message?.parts) || textOf(result?.parts);
	const status = result?.status?.state ?? FAILED;
	const outcome = toOutcome(status, text);

	if (!text && outcome !== 'laeuft') {
		console.error('Antwort des Schadensfallagenten ohne Text, Zustand:', status);
		return { outcome: 'gestoert', text: UNREACHABLE };
	}

	const recommendation = recommendationFrom(artifact);
	return {
		outcome,
		status,
		text: text || TAKEN_OVER,
		...(recommendation ? { empfehlung: recommendation } : {})
	};
}

function toOutcome(status: string, text: string): Outcome {
	if (status === COMPLETED) return 'geprueft';
	if (status === WORKING || status === SUBMITTED) return 'laeuft';
	if (status === FAILED) return 'gestoert';
	if (status !== REJECTED) return 'abgelehnt';

	const state = namedState(text);
	if (state === 'in_pruefung') return 'laeuft';
	if (state || CONTINUED_PHRASES.some((phrase) => text.includes(phrase))) {
		return 'weiterbearbeitet';
	}
	return 'abgelehnt';
}

function namedState(text: string): Schadensfallstatus | undefined {
	let found: Schadensfallstatus | undefined;
	let earliest = Number.MAX_SAFE_INTEGER;
	for (const state of KNOWN_STATES) {
		const at = text.indexOf(state);
		if (at >= 0 && at < earliest) {
			earliest = at;
			found = state;
		}
	}
	return found;
}

function error(message: { code?: number; message?: string }): PruefungResponse {
	console.error('Schadensfallagent meldete JSON-RPC-Fehler:', message);
	const wording = message.code === INTERNAL_ERROR ? '' : (message.message?.trim() ?? '');
	return { outcome: 'gestoert', text: wording || UNREACHABLE };
}

function url(): string {
	const raw = env.SCHADENSFALLAGENT_URL?.trim() || DEFAULT_URL;
	return raw.endsWith('/') ? raw : `${raw}/`;
}

function request(id: number) {
	return {
		jsonrpc: '2.0',
		id: randomUUID(),
		method: METHOD,
		params: {
			message: {
				kind: 'message',
				messageId: randomUUID(),
				role: 'user',
				parts: [
					{ kind: 'text', text: `Bitte prüfe Fall ${id}.` },
					{ kind: 'data', data: { [SCHADENSFALL_ID_KEY]: id } }
				]
			}
		}
	};
}

function recommendationFrom(artifact: Artifact | undefined): Bewertungsempfehlung | undefined {
	const data = artifact?.parts?.find((part) => part.data && typeof part.data === 'object');
	const content = (data?.data ?? {}) as Record<string, unknown>;
	const bewertung = content.bewertung as Record<string, unknown> | undefined;
	const value = bewertung?.empfehlung;
	return value === 'freigabe' || value === 'eskalation' ? value : undefined;
}

function textOf(parts: Part[] | undefined): string {
	if (!parts) return '';
	return parts
		.map((part) => part.text)
		.filter((text): text is string => typeof text === 'string')
		.join('');
}
