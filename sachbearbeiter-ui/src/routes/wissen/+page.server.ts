import { fail } from '@sveltejs/kit';
import { searchBedingungswerk, listDocuments } from '$lib/server/wissen';
import { tarifwerk } from '$lib/server/tarifwerk';
import { ApiError, type TarifId } from '$lib/types/api';
import type { Fundstelle } from '$lib/types/wissen';

const SEARCHABLE_TARIFE: TarifId[] = ['ATRA_DENT_S', 'ATRA_DENT_B', 'ATRA_DENT_X'];
const ALL = 'ALLE';

export async function load() {
	const tarife = tarifwerk().tarife.map((t) => ({
		id: t.schluessel,
		name: t.anzeigename
	}));
	try {
		const documents = await listDocuments();
		return { tarife, documents, wissenAvailable: true };
	} catch {
		return { tarife, documents: [], wissenAvailable: false };
	}
}

export const actions = {
	search: async ({ request }) => {
		const formData = await request.formData();
		const scope = String(formData.get('tarif') ?? '');
		const fachfrage = String(formData.get('frage') ?? '').trim();
		if (fachfrage.length < 3) {
			return fail(400, {
				searchError: 'Bitte eine Frage mit mindestens 3 Zeichen stellen',
				fachfrage
			});
		}

		const targets: TarifId[] =
			scope === ALL
				? SEARCHABLE_TARIFE
				: SEARCHABLE_TARIFE.includes(scope as TarifId) || scope === 'ATRA_DENT_X_SB'
					? [scope as TarifId]
					: [];
		if (targets.length === 0) {
			return fail(400, { searchError: 'Unbekannter Tarif', fachfrage });
		}

		const settled = await Promise.allSettled(
			targets.map(async (tarif) => ({
				tarif,
				result: await searchBedingungswerk({
					tarif,
					frage: fachfrage,
					anzahl: scope === ALL ? 5 : 8
				})
			}))
		);

		const hits: (Fundstelle & { tarif: TarifId })[] = [];
		const failures: string[] = [];
		for (const outcome of settled) {
			if (outcome.status === 'fulfilled') {
				for (const hit of outcome.value.result.treffer) {
					hits.push({ ...hit, tarif: outcome.value.tarif });
				}
			} else {
				failures.push(
					outcome.reason instanceof ApiError
						? outcome.reason.message
						: 'Der Wissensdienst ist nicht erreichbar'
				);
			}
		}

		if (hits.length === 0 && failures.length === targets.length) {
			return fail(502, { searchError: failures[0], fachfrage });
		}

		hits.sort((a, b) => b.bewertung - a.bewertung);
		return {
			fachfrage,
			scope,
			hits: hits.slice(0, 12),
			partialError: failures.length > 0 ? failures[0] : null
		};
	}
};
