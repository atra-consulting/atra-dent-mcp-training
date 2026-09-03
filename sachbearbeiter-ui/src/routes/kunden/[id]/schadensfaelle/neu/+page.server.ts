import { error, fail, redirect } from '@sveltejs/kit';
import { geldbetragFromInput } from '$lib/format';
import { backofficeApi } from '$lib/server/backoffice';
import { gozCatalog } from '$lib/server/goz';
import { tarifwerk } from '$lib/server/tarifwerk';
import { checkGoz } from '$lib/server/wissen';
import { ApiError, type Schadenposition } from '$lib/types/api';
import type { GozBefund } from '$lib/types/wissen';

export async function load({ params }) {
	const kunde = await backofficeApi().readKunde(Number(params.id));
	if (!kunde) error(404, 'Kunde nicht gefunden');
	let gozEntries: ReturnType<typeof gozCatalog> = [];
	try {
		gozEntries = gozCatalog();
	} catch {}
	return { kunde, leistungsbereiche: tarifwerk().leistungsbereiche, gozEntries };
}

function parsePositionen(
	raw: FormDataEntryValue | null
): { positionen: Schadenposition[] } | { error: string } {
	let parsed: unknown;
	try {
		parsed = JSON.parse(String(raw ?? '[]'));
	} catch {
		return { error: 'Positionen konnten nicht gelesen werden' };
	}
	if (!Array.isArray(parsed) || parsed.length === 0) {
		return { error: 'Mindestens eine Position erfassen' };
	}
	for (const p of parsed) {
		if (typeof p !== 'object' || p === null) return { error: 'Ungültige Position' };
		const { goz, leistungsbereich, betrag, beschreibung } = p as Record<string, unknown>;
		if (goz != null && goz !== '' && (typeof goz !== 'string' || !/^[0-9]{4}$/.test(goz)))
			return { error: `Ungültige GOZ-Nummer "${String(goz)}" (vierstellig oder leer)` };
		if (typeof betrag !== 'string' || geldbetragFromInput(betrag) === null)
			return { error: `"${String(betrag)}" ist kein Betrag — z. B. 450 oder 450,50` };
		if (typeof beschreibung !== 'string' || !beschreibung.trim())
			return { error: 'Jede Position braucht eine Beschreibung' };
		if (typeof leistungsbereich !== 'string') return { error: 'Position ohne Leistungsbereich' };
	}
	const positionen = (parsed as Schadenposition[]).map((p) => ({
		...p,
		goz: p.goz === '' || p.goz == null ? null : p.goz,
		betrag: geldbetragFromInput(p.betrag) ?? p.betrag
	}));
	return { positionen };
}

export const actions = {
	einreichen: async ({ params, request }) => {
		const formData = await request.formData();
		const behandlungsdatum = String(formData.get('behandlungsdatum') ?? '');
		const result = parsePositionen(formData.get('positionen'));
		if ('error' in result) return fail(400, { error: result.error });
		try {
			await backofficeApi().submitSchadensfall({
				kundenId: Number(params.id),
				behandlungsdatum,
				positionen: result.positionen
			});
		} catch (cause) {
			if (cause instanceof ApiError) return fail(cause.problem.status, { error: cause.message });
			throw cause;
		}
		redirect(303, `/kunden/${params.id}`);
	},

	gozPruefen: async ({ params, request }) => {
		const formData = await request.formData();
		let parsed: unknown;
		try {
			parsed = JSON.parse(String(formData.get('positionen') ?? '[]'));
		} catch {
			return fail(400, { error: 'Positionen konnten nicht gelesen werden' });
		}
		if (!Array.isArray(parsed) || parsed.length === 0) {
			return fail(400, { error: 'Mindestens eine Position erfassen' });
		}
		const nummern: string[] = [];
		const indices: number[] = [];
		for (const [index, p] of parsed.entries()) {
			const goz = typeof p === 'object' && p !== null ? (p as Record<string, unknown>).goz : null;
			if (goz == null || goz === '') continue;
			if (typeof goz !== 'string' || !/^[0-9]{4}$/.test(goz)) {
				return fail(400, {
					error: `Ungültige GOZ-Nummer "${String(goz)}" — zum Prüfen muss eine Nummer vierstellig sein`
				});
			}
			nummern.push(goz);
			indices.push(index);
		}
		if (nummern.length === 0) {
			return fail(400, { error: 'Zum Prüfen braucht mindestens eine Zeile eine GOZ-Nummer' });
		}

		const kunde = await backofficeApi().readKunde(Number(params.id));
		if (!kunde) error(404, 'Kunde nicht gefunden');

		try {
			const result = await checkGoz({ tarif: kunde.tarifId, nummern });
			const findings: (GozBefund | null)[] = parsed.map(() => null);
			indices.forEach((index, i) => (findings[index] = result.befunde[i] ?? null));
			return { findings };
		} catch (cause) {
			if (cause instanceof ApiError) return fail(cause.problem.status, { error: cause.message });
			return fail(502, { error: 'Der Wissensdienst ist nicht erreichbar' });
		}
	}
};
