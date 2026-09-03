import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { env } from '$env/dynamic/private';


const target = (() => {
	const directory = env.AGENTEN_TRACELOG?.trim();
	if (!directory) return null;
	try {
		mkdirSync(directory, { recursive: true });
		return join(directory, 'chat-ui.jsonl');
	} catch (error) {
		console.error('Tracelog: Verzeichnis nicht nutzbar, es wird nichts aufgezeichnet.', error);
		return null;
	}
})();

let failed = false;

export function tracelogEnabled(): boolean {
	return target !== null && !failed;
}

export function tracelogWrite(
	kind: string,
	context: string | undefined,
	fields: Record<string, unknown>
): void {
	if (!target || failed) return;
	try {
		const line = {
			timestamp: new Date().toISOString(),
			service: 'chat-ui',
			kind,
			...(context ? { context } : {}),
			...fields
		};
		appendFileSync(target, `${JSON.stringify(line)}\n`, 'utf8');
	} catch (error) {
		failed = true;
		console.error('Tracelog: Schreiben gescheitert, ab hier nichts mehr.', error);
	}
}
