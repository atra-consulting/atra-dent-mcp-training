import { env } from '$env/dynamic/private';
import type { AgentClient } from '$lib/types/chat';
import { A2aAgentClient, DEFAULT_URL } from './a2a';
import { MockAgentClient } from './mock';

let instance: AgentClient | null = null;

export function agentClient(): AgentClient {
	if (instance) return instance;

	const choice = env.AGENT_CLIENT?.trim() || 'a2a';
	switch (choice) {
		case 'a2a':
			instance = new A2aAgentClient(env.AGENT_URL?.trim() || DEFAULT_URL);
			break;
		case 'mock':
			instance = new MockAgentClient();
			break;
		default:
			throw new Error(
				`Unbekannter AGENT_CLIENT "${choice}" — bekannt sind "a2a" (Vorgabe) und "mock".`
			);
	}
	return instance;
}
