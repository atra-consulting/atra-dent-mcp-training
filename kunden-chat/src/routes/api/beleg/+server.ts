import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { BelegNotReadableError, extractRechnung } from '$lib/server/kernsystem';


const MAX_SIZE = 10 * 1024 * 1024;

const TYPE = 'application/pdf';

const NOT_READABLE =
	'Diese Datei konnte ich nicht lesen. Bitte laden Sie die Rechnung als PDF/A hoch, ' +
	'so wie die Praxis sie ausstellt.';

const UNAVAILABLE = 'Die Belegpruefung ist gerade nicht erreichbar.';

const refuse = (message: string, status: number) => json({ message }, { status });

export const POST: RequestHandler = async ({ request }) => {
	if (!request.headers.get('content-type')?.startsWith(TYPE)) {
		return refuse('Es lassen sich nur PDF-Dateien hochladen.', 415);
	}

	const length = Number(request.headers.get('content-length'));
	if (Number.isFinite(length) && length > MAX_SIZE) {
		return refuse(
			`Die Datei ist zu gross. Hoechstens ${MAX_SIZE / 1024 / 1024} MB sind moeglich.`,
			413
		);
	}

	if (!request.body) return refuse('Es kam keine Datei an.', 400);

	try {
		return json(await extractRechnung(request.body));
	} catch (cause) {
		if (cause instanceof BelegNotReadableError) {
			console.error('Kernsystem konnte den Beleg nicht auswerten:', cause.problem);
			return refuse(NOT_READABLE, 422);
		}
		console.error('Belegauswertung im Kernsystem fehlgeschlagen:', cause);
		return refuse(UNAVAILABLE, 502);
	}
};
