import { randomUUID } from 'node:crypto';
import type { RechnungsextraktionResult } from '$lib/types/api';
import { displayName } from '$lib/trace';
import type { AgentClient, AgentEvent, AgentInput, TracePoint } from '$lib/types/chat';
import { viewsFrom, type View } from '$lib/types/view';


export const DEFAULT_URL = 'http://localhost:8084/';

const KUNDEN_HEADER = 'x-kunden-id';

const BELEG_FIELD = 'rechnung';

const METHOD = 'message/stream';

const STATUS_UPDATE = 'status-update';
const ARTIFACT_UPDATE = 'artifact-update';

const INPUT_REQUIRED = 'input-required';

const TIMEOUT_MS = 300_000;

const MAX_CONVERSATIONS = 500;

const CHUNK_LENGTH = 40;

const UNAVAILABLE =
	'Ich bin gerade nicht erreichbar — bitte versuchen Sie es gleich noch einmal.';

const SENDER = 'chat-ui';

const PEER = 'orchestrator';


interface Part {
	kind?: string;
	text?: string;
	data?: unknown;
}

interface Message {
	parts?: Part[];
}

interface TaskStatus {
	state?: string;
	message?: Message;
}

interface Artifact {
	name?: string;
	parts?: Part[];
}

interface Result {
	kind?: string;
	final?: boolean;
	taskId?: string;
	contextId?: string;
	status?: TaskStatus;
	artifact?: Artifact;
	id?: string;
	artifacts?: Artifact[];
	parts?: Part[];
}

interface JsonRpcResponse {
	result?: Result;
	error?: { code?: number; message?: string };
}

export class A2aAgentClient implements AgentClient {
	readonly #url: string;

	readonly #openTasks = new Map<string, string>();

	constructor(url: string = DEFAULT_URL) {
		this.#url = url;
	}

	async *stream(input: AgentInput): AsyncIterable<AgentEvent> {
		try {
			yield* this.#run(input);
		} catch (cause) {
			console.error('A2A-Aufruf am Orchestrator fehlgeschlagen:', cause);
			yield { type: 'error', message: UNAVAILABLE };
		}
	}

	async *#run(input: AgentInput): AsyncIterable<AgentEvent> {
		const anliegen = input.history.at(-1)?.text.trim() ?? '';
		if (!anliegen && !input.beleg) {
			yield { type: 'error', message: 'Die Nachricht war leer.' };
			return;
		}

		const headers: Record<string, string> = {
			'Content-Type': 'application/json',
			Accept: 'text/event-stream'
		};
		if (input.customerId !== undefined) {
			headers[KUNDEN_HEADER] = String(input.customerId);
		}

		const openTask = this.#openTasks.get(input.conversationId);
		yield {
			type: 'trace',
			trace: {
				text: 'nehme Ihr Anliegen entgegen',
				label: 'Anliegen übermitteln',
				sender: SENDER,
				protocol: 'A2A',
				timestamp: new Date().toISOString(),
				peer: PEER,
				operation: METHOD,
				data: {
					message: anliegen,
					loggedIn: input.customerId !== undefined,
					...(input.customerId !== undefined ? { 'header x-kunden-id': input.customerId } : {}),
					contextId: input.conversationId,
					...(openTask ? { taskId: openTask } : { newContext: true })
				}
			}
		};

		const response = await fetch(this.#url, {
			method: 'POST',
			headers,
			body: JSON.stringify(this.#request(input.conversationId, openTask, anliegen, input.beleg)),
			signal: AbortSignal.timeout(TIMEOUT_MS)
		});

		if (!response.ok || !response.body) {
			console.error(`Orchestrator antwortete mit HTTP ${response.status} statt 200`);
			yield { type: 'error', message: UNAVAILABLE };
			return;
		}

		let lastArtifact: Artifact | undefined;

		for await (const event of events(response.body)) {
			if (event.error) {
				yield { type: 'error', message: event.error.message ?? UNAVAILABLE };
				return;
			}
			const result = event.result;
			if (!result) {
				continue;
			}

			if (result.kind === ARTIFACT_UPDATE) {
				if (result.artifact) lastArtifact = result.artifact;
				continue;
			}

			if (result.kind === STATUS_UPDATE) {
				if (!result.final) {
					const text = textOf(result.status?.message?.parts);
					if (text) {
						yield { type: 'status', text };
					}
					const reported = tracePoint(result.status?.message?.parts, text);
					if (reported) {
						yield { type: 'trace', trace: reported };
					}
					continue;
				}

				this.#remember(input.conversationId, result);
				yield* closing(result, lastArtifact);
				return;
			}

			if (result.kind === 'task' && isFinalState(result.status?.state)) {
				this.#remember(input.conversationId, { taskId: result.id, status: result.status });
				yield* closing(
					{ status: result.status, contextId: result.contextId },
					result.artifacts?.[0]
				);
				return;
			}
			if (result.kind === 'message') {
				const text = textOf(result.parts);
				if (text) {
					for (const chunk of chunks(text)) {
						yield { type: 'text-delta', text: chunk };
					}
				}
				yield { type: 'done' };
				return;
			}
		}

		console.error('Der Event-Stream des Orchestrators endete ohne Abschluss');
		yield { type: 'error', message: UNAVAILABLE };
	}

	#request(
		conversationId: string,
		taskId: string | undefined,
		text: string,
		beleg: RechnungsextraktionResult | undefined
	) {
		return {
			jsonrpc: '2.0',
			id: randomUUID(),
			method: METHOD,
			params: {
				message: {
					kind: 'message',
					messageId: randomUUID(),
					contextId: conversationId,
					...(taskId ? { taskId } : {}),
					role: 'user',
					parts: [
						{ kind: 'text', text },
						...(beleg ? [{ kind: 'data', data: { [BELEG_FIELD]: beleg } }] : [])
					]
				}
			}
		};
	}

	#remember(conversationId: string, result: Pick<Result, 'status' | 'taskId'>): void {
		if (result.status?.state === INPUT_REQUIRED && result.taskId) {
			this.#openTasks.delete(conversationId);
			this.#openTasks.set(conversationId, result.taskId);
			while (this.#openTasks.size > MAX_CONVERSATIONS) {
				const oldest = this.#openTasks.keys().next().value;
				if (oldest === undefined) break;
				this.#openTasks.delete(oldest);
			}
			return;
		}
		this.#openTasks.delete(conversationId);
	}
}


function isFinalState(state: string | undefined): boolean {
	return (
		state === 'completed' ||
		state === 'rejected' ||
		state === 'failed' ||
		state === 'canceled' ||
		state === 'unknown' ||
		state === INPUT_REQUIRED ||
		state === 'auth-required'
	);
}

function* closing(result: Result, artifact: Artifact | undefined): Generator<AgentEvent> {
	const text = textOf(artifact?.parts) || textOf(result.status?.message?.parts);
	if (!text) {
		console.error('Abschlussevent ohne Text, Zustand:', result.status?.state);
		yield { type: 'error', message: UNAVAILABLE };
		return;
	}
	yield { type: 'trace', trace: origin(result, artifact) };
	for (const chunk of chunks(text)) {
		yield { type: 'text-delta', text: chunk };
	}
	const views = extractViews(artifact);
	if (views.length > 0) {
		yield { type: 'views', views };
	}
	yield { type: 'done' };
}

function extractViews(artifact: Artifact | undefined): View[] {
	const part = artifact?.parts?.find((entry) => entry.data && typeof entry.data === 'object');
	const content = (part?.data ?? {}) as Record<string, unknown>;
	return viewsFrom(content.views);
}

export function origin(result: Result, artifact: Artifact | undefined): TracePoint {
	const part = artifact?.parts?.find((entry) => entry.data && typeof entry.data === 'object');
	const content = (part?.data ?? {}) as Record<string, unknown>;
	const source = typeof content.source === 'string' ? content.source : undefined;

	const data: Record<string, unknown> = { state: result.status?.state };
	if (artifact?.name) data.artifact = artifact.name;
	for (const [field, value] of Object.entries(content)) {
		if (field !== 'source') data[field] = value;
	}

	return {
		text: source ? `Antwort von ${displayName(source)}` : 'Antwort vom Orchestrator',
		sender: PEER,
		protocol: 'A2A',
		timestamp: new Date().toISOString(),
		peer: SENDER,
		operation: METHOD,
		data: source ? { source, ...data } : data
	};
}

function tracePoint(parts: Part[] | undefined, text: string): TracePoint | null {
	const data = parts?.find((entry) => entry.data && typeof entry.data === 'object');
	if (data) {
		const raw = data.data as Partial<TracePoint>;
		if (typeof raw.text === 'string' && typeof raw.protocol === 'string') {
			return raw as TracePoint;
		}
		console.error('data-Part eines Zwischenstands ist kein TracePoint');
	}
	return text ? { text, protocol: 'INTERNAL', timestamp: new Date().toISOString() } : null;
}

function textOf(parts: Part[] | undefined): string {
	if (!parts) return '';
	return parts
		.map((part) => part.text)
		.filter((text): text is string => typeof text === 'string')
		.join('');
}

function* chunks(text: string): Generator<string> {
	let buffer = '';
	for (const word of text.split(/(?<=\s)/)) {
		buffer += word;
		if (buffer.length >= CHUNK_LENGTH) {
			yield buffer;
			buffer = '';
		}
	}
	if (buffer) yield buffer;
}

async function* events(body: ReadableStream<Uint8Array>): AsyncIterable<JsonRpcResponse> {
	const reader = body.getReader();
	const decoder = new TextDecoder();
	let rest = '';
	try {
		for (;;) {
			const { value, done } = await reader.read();
			if (done) break;
			rest += decoder.decode(value, { stream: true });
			let newline = rest.indexOf('\n');
			while (newline >= 0) {
				const line = rest.slice(0, newline).replace(/\r$/, '');
				rest = rest.slice(newline + 1);
				newline = rest.indexOf('\n');
				if (!line.startsWith('data:')) continue;
				const payload = line.slice('data:'.length).trim();
				if (payload) yield JSON.parse(payload) as JsonRpcResponse;
			}
		}
	} finally {
		await reader.cancel().catch(() => undefined);
	}
}
