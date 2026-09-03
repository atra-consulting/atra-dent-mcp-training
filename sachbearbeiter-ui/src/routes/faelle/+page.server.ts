import type { PageServerLoad } from './$types';
import { backofficeApi } from '$lib/server/backoffice';
import type { Schadensfallstatus } from '$lib/types/api';

const ALL: Schadensfallstatus[] = [
	'eingereicht',
	'in_pruefung',
	'geprueft_freigabe',
	'geprueft_eskalation',
	'genehmigt',
	'abgelehnt',
	'ausgezahlt'
];

const DEFAULT_FILTER: Schadensfallstatus[] = [
	'geprueft_eskalation',
	'geprueft_freigabe',
	'eingereicht',
	'in_pruefung'
];

const RANK: Record<string, number> = {
	geprueft_eskalation: 0,
	geprueft_freigabe: 1,
	eingereicht: 2,
	in_pruefung: 3,
	genehmigt: 4,
	abgelehnt: 5,
	ausgezahlt: 6
};

export const load: PageServerLoad = async ({ url }) => {
	const requested = (url.searchParams.get('status') ?? '')
		.split(',')
		.filter((s): s is Schadensfallstatus => (ALL as string[]).includes(s));
	const filter = requested.length ? requested : DEFAULT_FILTER;

	const api = backofficeApi();
	const [schadensfaelle, kunden] = await Promise.all([
		api.listSchadensfaelle({ status: filter }),
		api.listKunden()
	]);

	const kundenNames = new Map(kunden.map((c) => [c.id, `${c.vorname} ${c.nachname}`] as const));

	schadensfaelle.sort(
		(a, b) =>
			(RANK[a.status] ?? 9) - (RANK[b.status] ?? 9) ||
			a.eingereichtAm.localeCompare(b.eingereichtAm)
	);

	return {
		schadensfaelle: schadensfaelle.map((schadensfall) => ({
			...schadensfall,
			kundenName: kundenNames.get(schadensfall.kundenId) ?? `Kunde ${schadensfall.kundenId}`
		})),
		filter,
		statusOptions: ALL
	};
};
