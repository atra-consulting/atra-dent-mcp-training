import { error, fail, redirect } from '@sveltejs/kit';
import { backofficeApi } from '$lib/server/backoffice';
import { kundeFromForm } from '$lib/server/kunde-form-data';
import { ApiError, type KundeAendern } from '$lib/types/api';

export async function load({ params }) {
	const api = backofficeApi();
	const kunde = await api.readKunde(Number(params.id));
	if (!kunde) error(404, 'Kunde nicht gefunden');
	return { kunde, tarife: await api.listTarife() };
}

export const actions = {
	default: async ({ params, request }) => {
		const id = Number(params.id);
		const api = backofficeApi();
		const existing = await api.readKunde(id);
		if (!existing) error(404, 'Kunde nicht gefunden');

		const input = kundeFromForm(await request.formData());
		const changes: KundeAendern = {};
		for (const field of [
			'vorname',
			'nachname',
			'geburtsdatum',
			'email',
			'telefon',
			'tarifId',
			'versicherungsbeginn',
			'vorversicherung',
			'fehlendeZaehne',
			'status'
		] as const) {
			if (input[field] !== existing[field])
				(changes as Record<string, unknown>)[field] = input[field];
		}
		if (JSON.stringify(input.adresse) !== JSON.stringify(existing.adresse))
			changes.adresse = input.adresse;

		try {
			const result = await api.updateKunde(id, changes);
			if (result === null) error(404, 'Kunde nicht gefunden');
		} catch (error) {
			if (error instanceof ApiError) return fail(error.problem.status, { error: error.message });
			throw error;
		}
		redirect(303, `/kunden/${id}`);
	}
};
