import type { UIMessage } from 'ai';
import type { Kunde, RechnungsextraktionResult, Schadensfall } from './api';
import type { View } from './view';

export type ChatUIMessage = UIMessage<
	{ belegFile?: string },
	{ status: { text: string }; trace: TracePoint; view: View }
>;

export interface ChatMessage {
	role: 'customer' | 'agent';
	text: string;
}

export type Protocol = 'A2A' | 'MCP' | 'MODELL' | 'INTERNAL';

export interface TracePoint {
	text: string;
	label?: string;
	sender?: string;
	protocol: Protocol;
	peer?: string;
	operation?: string;
	data?: Record<string, unknown>;
	timestamp?: string;
}

export type AgentEvent =
	| { type: 'text-delta'; text: string }
	| { type: 'status'; text: string }
	| { type: 'trace'; trace: TracePoint }
	| { type: 'views'; views: View[] }
	| { type: 'done' }
	| { type: 'error'; message: string };

export interface CustomerContext {
	customer: Kunde;
	claims: Schadensfall[];
}

export interface AgentInput {
	history: ChatMessage[];
	conversationId: string;
	customerId?: number;
	beleg?: RechnungsextraktionResult;
}

export interface AgentClient {
	stream(input: AgentInput): AsyncIterable<AgentEvent>;
}
