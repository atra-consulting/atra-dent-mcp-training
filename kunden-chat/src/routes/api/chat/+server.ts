import { randomUUID } from 'node:crypto';
import { createUIMessageStream, createUIMessageStreamResponse } from 'ai';
import type { UIMessage } from 'ai';
import type { Cookies } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { agentClient } from '$lib/server/agent';
import { CONVERSATION_COOKIE } from '$lib/server/conversation';
import { tracelogWrite } from '$lib/server/tracelog';
import type { RechnungsextraktionResult } from '$lib/types/api';
import type { ChatMessage, ChatUIMessage } from '$lib/types/chat';

function toChatMessages(messages: UIMessage[]): ChatMessage[] {
	return messages.map((message) => ({
		role: message.role === 'user' ? ('customer' as const) : ('agent' as const),
		text: message.parts.flatMap((part) => (part.type === 'text' ? [part.text] : [])).join('')
	}));
}

function conversationId(cookies: Cookies): string {
	const existing = cookies.get(CONVERSATION_COOKIE);
	if (existing) return existing;
	const fresh = randomUUID();
	cookies.set(CONVERSATION_COOKIE, fresh, { path: '/', httpOnly: true, sameSite: 'lax' });
	return fresh;
}

export const POST: RequestHandler = async ({ request, cookies }) => {
	let history: ChatMessage[] = [];
	let beleg: RechnungsextraktionResult | undefined;
	try {
		const body = (await request.json()) as {
			messages?: UIMessage[];
			beleg?: RechnungsextraktionResult;
		};
		if (!Array.isArray(body.messages)) return new Response('Ungültige Anfrage', { status: 400 });
		history = toChatMessages(body.messages);
		beleg = body.beleg ?? undefined;
	} catch {
		return new Response('Ungültige Anfrage', { status: 400 });
	}

	const signedIn = Number(cookies.get('demo-kunde'));
	const customerId = Number.isInteger(signedIn) && signedIn > 0 ? signedIn : undefined;
	const conversation = conversationId(cookies);

	tracelogWrite('request', conversation, {
		anliegen: history.at(-1)?.text ?? '',
		historyLength: history.length,
		loggedIn: customerId !== undefined,
		'header x-kunden-id': customerId ?? null,
		beleg: beleg ?? null
	});

	const stream = createUIMessageStream<ChatUIMessage>({
		async execute({ writer }) {
			const textId = 'response';
			let textOpen = false;
			let response = '';
			try {
				for await (const event of agentClient().stream({
					history,
					conversationId: conversation,
					customerId,
					beleg
				})) {
					if (event.type === 'status') {
						writer.write({ type: 'data-status', data: { text: event.text }, transient: true });
					} else if (event.type === 'trace') {
						writer.write({ type: 'data-trace', data: event.trace });
					} else if (event.type === 'text-delta') {
						if (!textOpen) {
							writer.write({ type: 'text-start', id: textId });
							textOpen = true;
						}
						response += event.text;
						writer.write({ type: 'text-delta', id: textId, delta: event.text });
					} else if (event.type === 'views') {
						for (const view of event.views) {
							writer.write({ type: 'data-view', data: view });
						}
					} else if (event.type === 'error') {
						tracelogWrite('error', conversation, { message: event.message });
						writer.write({ type: 'error', errorText: event.message });
					}
				}
			} catch (cause) {
				console.error('Agent-Stream fehlgeschlagen:', cause);
				tracelogWrite('error', conversation, {
					message: cause instanceof Error ? cause.message : String(cause)
				});
				writer.write({
					type: 'error',
					errorText: 'Ich bin gerade nicht erreichbar — bitte versuchen Sie es gleich noch einmal.'
				});
			}
			if (textOpen) writer.write({ type: 'text-end', id: textId });
			tracelogWrite('answer', conversation, { text: response });
		}
	});

	return createUIMessageStreamResponse({ stream });
};
