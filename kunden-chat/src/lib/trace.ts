import type { ChatUIMessage, TracePoint } from '$lib/types/chat';


export type DisplayPart =
	| { kind: 'text'; text: string }
	| {
			kind: 'trace';
			trace: TracePoint;
			result?: unknown;
			failed?: unknown;
			resultTime?: string;
			level: number;
			returnHop: boolean;
	  };

const NAMES: Record<string, string> = {
	'chat-ui': 'Chat-UI',
	orchestrator: 'Orchestrator',
	beratung: 'Beratungsagent',
	schadensfall: 'Schadensfallagent'
};

export function actor(id: string | undefined): string | undefined {
	if (!id) return undefined;
	if (NAMES[id]) return id;
	const plain = letters(id);
	return Object.keys(NAMES).find((key) => plain.endsWith(letters(NAMES[key]))) ?? id;
}

export function displayName(id: string | undefined): string | undefined {
	const key = actor(id);
	return key === undefined ? undefined : (NAMES[key] ?? key);
}

function letters(name: string): string {
	return name.toLowerCase().replace(/[^a-z]/g, '');
}

export interface Level {
	depth: number;
	returnHop: boolean;
}

export function levels(points: TracePoint[]): Level[] {
	const stack: string[] = [];
	return points.map((point) => {
		const depth = place(stack, actor(point.sender));
		const peer = actor(point.peer);
		if (point.protocol !== 'A2A' || !peer) {
			return { depth, returnHop: false };
		}
		const open = stack.indexOf(peer);
		if (open === -1) {
			stack.push(peer);
			return { depth, returnHop: false };
		}
		stack.length = open + 1;
		return { depth, returnHop: true };
	});
}

function place(stack: string[], sender: string | undefined): number {
	if (!sender) return Math.max(0, stack.length - 1);
	const seen = stack.indexOf(sender);
	if (seen !== -1) {
		stack.length = seen + 1;
		return seen;
	}
	stack.push(sender);
	return stack.length - 1;
}

export function offset(timestamp: string | undefined, start: string | undefined) {
	return duration(start, timestamp);
}

export function duration(from: string | undefined, to: string | undefined) {
	const ms = distance(from, to);
	return ms === undefined ? undefined : span(ms);
}

function distance(from: string | undefined, to: string | undefined): number | undefined {
	if (!from || !to) return undefined;
	const start = Date.parse(from);
	const end = Date.parse(to);
	if (Number.isNaN(start) || Number.isNaN(end)) return undefined;
	return end - start;
}

function span(ms: number): string {
	if (Math.abs(ms) < 1000) return `${Math.round(ms)} ms`;
	return `${(ms / 1000).toLocaleString('de-DE', {
		minimumFractionDigits: 1,
		maximumFractionDigits: 1
	})} s`;
}

export function transport(field: string): boolean {
	return field.startsWith('header');
}

export function panelFields(
	trace: TracePoint,
	result: unknown,
	failed: unknown
): [string, unknown][] {
	const fields: [string, unknown][] = [];
	if (result !== undefined) fields.push(['result', result]);
	if (failed !== undefined) fields.push(['failed', failed]);

	const rest = Object.entries(trace.data ?? {}).filter(([field]) => field !== 'call');
	fields.push(...rest.filter(([field]) => !transport(field)));
	fields.push(...rest.filter(([field]) => transport(field)));
	return fields;
}

export function unpack(value: unknown, depth = 0): unknown {
	if (depth > 3) return value;
	if (typeof value === 'string') {
		const trimmed = value.trim();
		if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return value;
		try {
			return unpack(JSON.parse(trimmed), depth + 1);
		} catch {
			return value;
		}
	}
	if (Array.isArray(value)) {
		const parts = value.map((entry) => unpack(entry, depth + 1));
		const content = parts.length === 1 ? onlyText(parts[0]) : undefined;
		return content === undefined ? parts : content;
	}
	if (value && typeof value === 'object') {
		return Object.fromEntries(
			Object.entries(value).map(([field, content]) => [field, unpack(content, depth + 1)])
		);
	}
	return value;
}

function onlyText(part: unknown): unknown {
	if (!part || typeof part !== 'object' || Array.isArray(part)) return undefined;
	const fields = Object.keys(part);
	if (fields.length !== 1 || fields[0] !== 'text') return undefined;
	return (part as { text: unknown }).text;
}

export function withElapsed(text: string, elapsed: string | undefined): string {
	return elapsed ? `${text} · ${elapsed}` : text;
}

export function displayParts(message: ChatUIMessage): DisplayPart[] {
	const parts: DisplayPart[] = [];
	for (const part of message.parts) {
		if (part.type === 'text') {
			parts.push({ kind: 'text', text: part.text });
			continue;
		}
		if (part.type !== 'data-trace') continue;

		const trace = part.data;
		const traces = parts.filter(
			(part): part is Extract<DisplayPart, { kind: 'trace' }> => part.kind === 'trace'
		);
		const open = traces.findLast(
			(part) =>
				part.result === undefined &&
				part.failed === undefined &&
				isReturnHop(part.trace, trace)
		);
		if (open) {
			open.result = trace.data?.result;
			open.failed = trace.data?.failed;
			if (traces.at(-1) === open) {
				open.resultTime = trace.timestamp;
			}
			continue;
		}
		parts.push({ kind: 'trace', trace, level: 0, returnHop: false });
	}

	const traces = parts.filter(
		(part): part is Extract<DisplayPart, { kind: 'trace' }> => part.kind === 'trace'
	);
	levels(traces.map((part) => part.trace)).forEach((level, i) => {
		traces[i].level = level.depth;
		traces[i].returnHop = level.returnHop;
	});
	return parts;
}

function isReturnHop(forward: TracePoint, back: TracePoint): boolean {
	return (
		(forward.protocol === 'MCP' || forward.protocol === 'MODELL') &&
		forward.protocol === back.protocol &&
		forward.sender === back.sender &&
		forward.operation === back.operation &&
		forward.data?.call !== undefined &&
		forward.data.call === back.data?.call
	);
}

export function author(message: ChatUIMessage): string | null {
	const closing = message.parts.findLast(
		(part) => part.type === 'data-trace' && part.data.protocol === 'A2A' && part.data.data
	);
	if (!closing || closing.type !== 'data-trace') return null;

	const data = closing.data.data ?? {};
	if (!('state' in data)) return null;

	const source = typeof data.source === 'string' ? data.source : undefined;
	const orchestrator = displayName(closing.data.sender);
	if (!source) {
		return `${orchestrator ?? 'Orchestrator'} · selbst beantwortet`;
	}
	return `${displayName(source)} · über ${orchestrator} · A2A`;
}
