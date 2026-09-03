import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { backofficeApi } from '$lib/server/backoffice';

export const load: PageServerLoad = async ({ params }) => {
	const id = Number(params.id);
	if (!Number.isInteger(id)) error(404, 'Schadensfall nicht gefunden');
	const schadensfall = await backofficeApi().readSchadensfall(id);
	if (!schadensfall) error(404, 'Schadensfall nicht gefunden');
	return { schadensfall };
};
