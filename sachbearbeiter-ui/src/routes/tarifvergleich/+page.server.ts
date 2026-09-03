import { fail } from '@sveltejs/kit';
import { backofficeApi } from '$lib/server/backoffice';
import { listDocuments } from '$lib/server/wissen';
import { tarifwerk } from '$lib/server/tarifwerk';
import { ApiError, type TarifId } from '$lib/types/api';

export async function load() {
	const tarifwerkData = tarifwerk();
	const bedingungswerkPdfs: Partial<Record<TarifId, string>> = Object.fromEntries(
		(await listDocuments().catch(() => []))
			.filter((d) => d.art === 'BEDINGUNGSWERK' && d.tarif)
			.map((d) => [d.tarif, d.pdfUrl])
	);
	bedingungswerkPdfs.ATRA_DENT_X_SB ??= bedingungswerkPdfs.ATRA_DENT_X;
	return {
		tarifDtos: await backofficeApi().listTarife(),
		tarife: tarifwerkData.tarife,
		leistungsbereiche: tarifwerkData.leistungsbereiche,
		staffeln: tarifwerkData.staffeln,
		bedingungswerkPdfs
	};
}

export const actions = {
	berechnen: async ({ request }) => {
		const formData = await request.formData();
		const geburtsdatum = String(formData.get('geburtsdatum') ?? '');
		const gewuenschterBeginn = String(formData.get('gewuenschterBeginn') ?? '');
		if (!geburtsdatum || !gewuenschterBeginn) {
			return fail(400, { formError: 'Geburtsdatum und gewünschter Beginn sind erforderlich' });
		}

		const api = backofficeApi();
		const results: Record<string, { monatsbeitrag?: string; error?: string }> = {};
		const tarifIds: TarifId[] = ['ATRA_DENT_S', 'ATRA_DENT_B', 'ATRA_DENT_X', 'ATRA_DENT_X_SB'];
		await Promise.all(
			tarifIds.map(async (tarifId) => {
				try {
					const result = await api.calculateBeitrag({
						geburtsdatum: geburtsdatum,
						tarifId,
						gewuenschterBeginn: gewuenschterBeginn
					});
					results[tarifId] = { monatsbeitrag: result.monatsbeitrag };
				} catch (error) {
					results[tarifId] = {
						error: error instanceof ApiError ? error.message : 'Berechnung fehlgeschlagen'
					};
				}
			})
		);
		return { results, geburtsdatum, gewuenschterBeginn };
	}
};
