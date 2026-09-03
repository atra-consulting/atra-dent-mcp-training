import { avatarUrl } from '$lib/server/avatar';
import { backofficeApi } from '$lib/server/backoffice';
import { beitragsmonateSince } from '$lib/versicherung';

export async function load() {
	const api = backofficeApi();
	const [kunden, schadensfaelle, tarife] = await Promise.all([
		api.listKunden(),
		api.listSchadensfaelle().catch(() => []),
		api.listTarife().catch(() => [])
	]);

	const beitraege = new Map(tarife.map((t) => [t.id, Number.parseFloat(t.basisbeitragMonatlich)]));
	const tarifNames = new Map(tarife.map((t) => [t.id, t.name]));
	const erstattungPerKunde = new Map<number, number>();
	for (const schadensfall of schadensfaelle) {
		erstattungPerKunde.set(
			schadensfall.kundenId,
			(erstattungPerKunde.get(schadensfall.kundenId) ?? 0) +
				Number.parseFloat(schadensfall.erstattungsbetrag ?? '0')
		);
	}

	return {
		kunden: kunden.map((kunde) => {
			const beitrag = beitraege.get(kunde.tarifId);
			const paid =
				beitrag === undefined ? null : beitragsmonateSince(kunde.versicherungsbeginn) * beitrag;
			return {
				...kunde,
				tarifName: tarifNames.get(kunde.tarifId) ?? kunde.tarifId,
				avatar: avatarUrl(kunde),
				balance: paid === null ? null : paid - (erstattungPerKunde.get(kunde.id) ?? 0)
			};
		})
	};
}
