import { fail, redirect } from '@sveltejs/kit';
import { backofficeApi } from '$lib/server/backoffice';
import { kundeFromForm } from '$lib/server/kunde-form-data';
import { ApiError } from '$lib/types/api';

export async function load() {
	return { tarife: await backofficeApi().listTarife() };
}

export const actions = {
	default: async ({ request }) => {
		const data = kundeFromForm(await request.formData());
		let newId: number;
		try {
			newId = (await backofficeApi().createKunde(data)).id;
		} catch (error) {
			if (error instanceof ApiError) return fail(error.problem.status, { error: error.message });
			throw error;
		}
		redirect(303, `/kunden/${newId}`);
	}
};
