import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { backofficeApi } from '$lib/server/backoffice';
import { checkSchadensfall } from '$lib/server/schadensfallagent';
import { geldbetragFromInput } from '$lib/format';
import {
	ApiError,
	type Ablehnungsgrund,
	type SchadensfallAendern
} from '$lib/types/api';

export const load: PageServerLoad = async ({ params }) => {
	const id = Number(params.id);
	if (!Number.isInteger(id)) error(404, 'Schadensfall nicht gefunden');
	const api = backofficeApi();
	const schadensfall = await api.readSchadensfall(id);
	if (!schadensfall) error(404, 'Schadensfall nicht gefunden');
	const kunde = await api.readKunde(schadensfall.kundenId);
	return { schadensfall, kunde };
};

async function update(id: number, changes: SchadensfallAendern) {
	try {
		const result = await backofficeApi().updateSchadensfall(id, changes);
		if (result === null) return fail(404, { error: 'Schadensfall nicht gefunden' });
		return { done: true };
	} catch (e) {
		if (e instanceof ApiError) return fail(e.problem.status ?? 500, { error: e.message });
		throw e;
	}
}

export const actions: Actions = {
	genehmigen: async ({ params, request }) => {
		const form = await request.formData();
		const geldbetrag = geldbetragFromInput(String(form.get('erstattungsbetrag') ?? ''));
		if (geldbetrag === null)
			return fail(400, { error: 'Bitte einen gültigen Erstattungsbetrag angeben.' });
		return update(Number(params.id), { status: 'genehmigt', erstattungsbetrag: geldbetrag });
	},
	ablehnen: async ({ params, request }) => {
		const form = await request.formData();
		const ablehnungsgrund = form.get('ablehnungsgrund');
		if (!ablehnungsgrund) return fail(400, { error: 'Bitte einen Ablehnungsgrund wählen.' });
		const ablehnungshinweis = String(form.get('ablehnungshinweis') ?? '').trim();
		return update(Number(params.id), {
			status: 'abgelehnt',
			erstattungsbetrag: '0.00',
			ablehnungsgrund: String(ablehnungsgrund) as Ablehnungsgrund,
			...(ablehnungshinweis ? { ablehnungshinweis } : {})
		});
	},
	auszahlen: async ({ params }) => update(Number(params.id), { status: 'ausgezahlt' }),

	erneutPruefen: async ({ params }) => {
		const id = Number(params.id);
		let schadensfall;
		try {
			schadensfall = await backofficeApi().updateSchadensfall(id, {
				status: 'eingereicht',
				akteur: 'sachbearbeitung'
			});
		} catch (e) {
			if (e instanceof ApiError) return fail(e.problem.status ?? 500, { error: e.message });
			throw e;
		}
		if (schadensfall === null) return fail(404, { error: 'Schadensfall nicht gefunden' });
		return { done: true, agent: await checkSchadensfall(id, schadensfall.kundenId) };
	}
};
