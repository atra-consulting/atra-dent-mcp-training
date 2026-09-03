import { error, fail } from '@sveltejs/kit';
import { geldbetragFromInput } from '$lib/format';
import { avatarUrl } from '$lib/server/avatar';
import { backofficeApi } from '$lib/server/backoffice';
import { staffelbetrag, tarifwerk } from '$lib/server/tarifwerk';
import { listDocuments } from '$lib/server/wissen';
import { currentVersicherungsjahr, inVersicherungsjahr } from '$lib/versicherung';
import {
	ApiError,
	type Ablehnungsgrund,
	type Geldbetrag,
	type LeistungsbereichSchluessel,
	type SchadensfallAendern,
	type Schadensfallstatus
} from '$lib/types/api';

export async function load({ params }) {
	const id = Number(params.id);
	if (!Number.isInteger(id) || id < 1) error(404, 'Kunde nicht gefunden');

	const api = backofficeApi();
	const [kunde, schadensfaelle, tarife] = await Promise.all([
		api.readKunde(id),
		api.listKundenSchadensfaelle(id),
		api.listTarife().catch(() => [])
	]);
	if (!kunde || schadensfaelle === null) error(404, 'Kunde nicht gefunden');

	const bedingungswerkDocument = await listDocuments({ tarif: kunde.tarifId })
		.then((documents) => documents.find((d) => d.art === 'BEDINGUNGSWERK') ?? null)
		.catch(() => null);

	const tarifwerkData = tarifwerk();
	const tarif = tarifwerkData.tarife.find((t) => t.schluessel === kunde.tarifId);
	const versicherungsjahr = currentVersicherungsjahr(kunde.versicherungsbeginn);
	const sumErstattung = (items: typeof schadensfaelle) =>
		items.reduce(
			(sum, schadensfall) => sum + Number.parseFloat(schadensfall.erstattungsbetrag ?? '0'),
			0
		);
	const erstattungTotal = sumErstattung(schadensfaelle);

	const monatsbeitrag = tarife.find((t) => t.id === kunde.tarifId)?.basisbeitragMonatlich ?? null;
	const beitragsmonate = (() => {
		const [year, month, day] = kunde.versicherungsbeginn.split('-').map(Number);
		const versicherungsbeginn = new Date(year, month - 1, day);
		const today = new Date();
		if (today < versicherungsbeginn) return 0;
		let months =
			(today.getFullYear() - versicherungsbeginn.getFullYear()) * 12 +
			(today.getMonth() - versicherungsbeginn.getMonth());
		if (today.getDate() < versicherungsbeginn.getDate()) months -= 1;
		return Math.max(1, months + 1);
	})();
	const beitraegeTotal =
		monatsbeitrag === null ? null : beitragsmonate * Number.parseFloat(monatsbeitrag);

	const openSchadensfaelle = schadensfaelle.filter(
		(schadensfall) => schadensfall.status === 'eingereicht' || schadensfall.status === 'in_pruefung'
	);

	return {
		kunde,
		schadensfaelle,
		avatar: avatarUrl(kunde),
		tarifName: tarif?.anzeigename ?? kunde.tarifId,
		tarifPositioning: tarif?.positionierung ?? null,
		bedingungswerkDocument,
		monatsbeitrag,
		selbstbehalt: tarif?.selbstbehalt ?? null,
		jahreshoechstgrenze: tarif?.jahreshoechstgrenze ?? null,
		versicherungsjahr,
		staffelLimit: staffelbetrag(tarifwerkData, kunde.tarifId, versicherungsjahr),
		erstattungTotal,
		erstattungCurrentYear: sumErstattung(
			schadensfaelle.filter((schadensfall) =>
				inVersicherungsjahr(
					schadensfall.behandlungsdatum,
					kunde.versicherungsbeginn,
					versicherungsjahr
				)
			)
		),
		beitragsmonate,
		beitraegeTotal,
		balance: beitraegeTotal === null ? null : beitraegeTotal - erstattungTotal,
		lossRatio: beitraegeTotal ? erstattungTotal / beitraegeTotal : null,
		openSchadensfaelleCount: openSchadensfaelle.length,
		openSchadensfaelleTotal: openSchadensfaelle.reduce(
			(sum, schadensfall) => sum + Number.parseFloat(schadensfall.rechnungsbetrag),
			0
		)
	};
}

export const actions = {
	schadensfallAktualisieren: async ({ request }) => {
		const formData = await request.formData();
		const schadensfallId = Number(formData.get('schadensfallId'));
		const status = String(formData.get('status')) as Schadensfallstatus;
		const geldbetragInput = String(formData.get('erstattungsbetrag') ?? '').trim();
		const ablehnungsgrund = String(formData.get('ablehnungsgrund') ?? '').trim();
		const ablehnungshinweis = String(formData.get('ablehnungshinweis') ?? '').trim();

		const erstattungsbetrag = geldbetragInput ? geldbetragFromInput(geldbetragInput) : '';
		if (erstattungsbetrag === null) {
			return fail(400, {
				editError: `"${geldbetragInput}" ist kein Betrag — z. B. 45 oder 45,50`,
				schadensfallId
			});
		}
		if (status === 'abgelehnt' && !ablehnungsgrund) {
			return fail(400, {
				editError: 'Bei Ablehnung ist ein Ablehnungsgrund erforderlich',
				schadensfallId
			});
		}

		const changes: SchadensfallAendern = { status };
		if (erstattungsbetrag) changes.erstattungsbetrag = erstattungsbetrag;
		if (ablehnungsgrund) changes.ablehnungsgrund = ablehnungsgrund as Ablehnungsgrund;
		if (ablehnungshinweis) changes.ablehnungshinweis = ablehnungshinweis;

		try {
			const result = await backofficeApi().updateSchadensfall(schadensfallId, changes);
			if (result === null) {
				return fail(404, { editError: 'Schadensfall nicht gefunden', schadensfallId });
			}
		} catch (error) {
			if (error instanceof ApiError)
				return fail(error.problem.status, { editError: error.message, schadensfallId });
			throw error;
		}
		return { edited: schadensfallId };
	},

	erstattungBerechnen: async ({ params, request }) => {
		const formData = await request.formData();
		const schadensfallId = Number(formData.get('schadensfallId'));
		const gkvInput = String(formData.get('gkvLeistung') ?? '').trim();
		const unfallbedingt = formData.get('unfallbedingt') !== null;

		const gkvLeistung = gkvInput ? geldbetragFromInput(gkvInput) : '';
		if (gkvLeistung === null) {
			return fail(400, {
				editError: `"${gkvInput}" ist kein Betrag — z. B. 600 oder 600,50`,
				schadensfallId
			});
		}

		const api = backofficeApi();
		const kundenId = Number(params.id);
		const [kunde, schadensfaelle] = await Promise.all([
			api.readKunde(kundenId),
			api.listKundenSchadensfaelle(kundenId)
		]);
		if (!kunde || schadensfaelle === null) error(404, 'Kunde nicht gefunden');
		const schadensfall = schadensfaelle.find((c) => c.id === schadensfallId);
		if (!schadensfall)
			return fail(404, { editError: 'Schadensfall nicht gefunden', schadensfallId });
		if (schadensfall.positionen.some((p) => p.leistungsbereich === null)) {
			return fail(400, {
				editError:
					'Erstattung kann erst berechnet werden, wenn alle Positionen einem Leistungsbereich zugeordnet sind.',
				schadensfallId
			});
		}

		const otherPaid = schadensfaelle.filter(
			(c) => c.id !== schadensfallId && c.erstattungsbetrag !== null
		);
		const sumErstattung = (items: typeof schadensfaelle) =>
			items.reduce((sum, c) => sum + Number.parseFloat(c.erstattungsbetrag ?? '0'), 0);

		const [year, month, day] = schadensfall.behandlungsdatum.split('-').map(Number);
		const schadensfallYear = currentVersicherungsjahr(
			kunde.versicherungsbeginn,
			new Date(year, month - 1, day)
		);
		const sameYearPaid = otherPaid.filter((c) =>
			inVersicherungsjahr(c.behandlungsdatum, kunde.versicherungsbeginn, schadensfallYear)
		);

		const perLeistungsbereich = (items: typeof schadensfaelle) => {
			const sums: Partial<Record<LeistungsbereichSchluessel, Geldbetrag>> = {};
			for (const other of items) {
				const leistungsbereiche = [...new Set(other.positionen.map((p) => p.leistungsbereich))];
				if (leistungsbereiche.length !== 1 || leistungsbereiche[0] === null) continue;
				const leistungsbereich = leistungsbereiche[0];
				sums[leistungsbereich] = (
					Number.parseFloat(sums[leistungsbereich] ?? '0') +
					Number.parseFloat(other.erstattungsbetrag ?? '0')
				).toFixed(2);
			}
			return sums;
		};

		try {
			const result = await api.calculateErstattung({
				tarifId: kunde.tarifId,
				versicherungsbeginn: kunde.versicherungsbeginn,
				behandlungsdatum: schadensfall.behandlungsdatum,
				positionen: schadensfall.positionen,
				gkvLeistung: gkvLeistung || undefined,
				unfallbedingt: unfallbedingt,
				verbrauch: {
					staffel: sumErstattung(otherPaid).toFixed(2),
					year: sumErstattung(sameYearPaid).toFixed(2),
					leistungsbereiche: perLeistungsbereich(sameYearPaid),
					leistungsbereicheSeitVersicherungsbeginn: perLeistungsbereich(otherPaid)
				}
			});
			return { calculated: result, schadensfallId };
		} catch (cause) {
			if (cause instanceof ApiError)
				return fail(cause.problem.status, { editError: cause.message, schadensfallId });
			throw cause;
		}
	}
};
