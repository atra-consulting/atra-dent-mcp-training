import { readCustomerContext } from '$lib/server/kernsystem';
import { tarifwerk } from '$lib/server/tarifwerk';
import type { LeistungsbereichSchluessel, TarifId } from '$lib/types/api';
import type { AgentClient, AgentEvent, AgentInput, CustomerContext } from '$lib/types/chat';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const currency = (value: number | string) =>
	new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(
		typeof value === 'string' ? Number.parseFloat(value) : value
	);

const areaKeywords: [RegExp, LeistungsbereichSchluessel][] = [
	[/implantat/, 'IMP'],
	[/inlay|onlay/, 'INL'],
	[/zahnersatz|krone|bruecke|brücke|prothese/, 'ZE'],
	[/fuellung|füllung|wurzel|zahnerhalt/, 'ZERH'],
	[/parodont/, 'PAR'],
	[/prophylaxe|zahnreinigung|pzr/, 'PZR'],
	[/kieferortho|kfo|zahnspange/, 'KFO'],
	[/funktionsanalyse|aufbissschiene|schiene/, 'FUN'],
	[/narkose|sedierung/, 'NAR'],
	[/akut|schmerz/, 'AKUT']
];

function detectArea(text: string): LeistungsbereichSchluessel | null {
	for (const [pattern, key] of areaKeywords) if (pattern.test(text)) return key;
	return null;
}

function detectTariff(text: string): TarifId | null {
	if (/brillant.*selbstbehalt|selbstbehalt.*brillant|x_sb/.test(text)) return 'ATRA_DENT_X_SB';
	if (/brillant/.test(text)) return 'ATRA_DENT_X';
	if (/balance/.test(text)) return 'ATRA_DENT_B';
	if (/smart/.test(text)) return 'ATRA_DENT_S';
	return null;
}

function contractAnswer(context: CustomerContext | undefined): string {
	if (!context) {
		return 'Für Fragen zu Ihrem Vertrag melden Sie sich bitte oben rechts an (Demo-Anmeldung). Danach kann ich Ihnen zu Tarif, Versicherungsbeginn und Ihren Schadensfällen Auskunft geben.';
	}
	const catalog = tarifwerk();
	const tariffName =
		catalog.tarife.find((t) => t.schluessel === context.customer.tarifId)?.anzeigename ??
		context.customer.tarifId;
	const claims = context.claims;
	let text = `Gerne, ${context.customer.vorname}! Sie sind im Tarif ${tariffName} versichert, Versicherungsbeginn ${new Date(context.customer.versicherungsbeginn).toLocaleDateString('de-DE')}. `;
	if (claims.length === 0) {
		text += 'Es liegen noch keine Schadensfälle vor.';
	} else {
		text += `Es liegen ${claims.length} Schadensfälle vor: `;
		text += claims
			.map((claim) => {
				const status =
					claim.status === 'abgelehnt'
						? `abgelehnt (${claim.ablehnungshinweis ?? claim.ablehnungsgrund})`
						: claim.erstattungsbetrag
							? `${claim.status}, erstattet ${currency(claim.erstattungsbetrag)}`
							: claim.status;
				return `Behandlung vom ${new Date(claim.behandlungsdatum).toLocaleDateString('de-DE')} über ${currency(claim.rechnungsbetrag)} — ${status}`;
			})
			.join('; ');
		text += '.';
	}
	return text;
}

function recommendationAnswer(): string {
	return (
		'Das hängt von Ihren Wünschen ab — eine kurze Orientierung: ' +
		'Wenn Sie eine günstige Grundabsicherung für Zahnersatz und Zahnerhalt suchen, passt atra.dent.smart. ' +
		'Den besten Allround-Schutz inklusive Implantaten und Kieferorthopädie bietet atra.dent.balance (100 EUR Selbstbehalt pro Jahr). ' +
		'Wenn Sie maximale Erstattung ohne Eigenanteil wollen, ist atra.dent.brillant die erste Wahl — ' +
		'mit 250 EUR Selbstbehalt auch als günstigere Variante brillant mit Selbstbehalt. ' +
		'Verraten Sie mir gewünschte Leistungen und Budget, dann werde ich konkreter.'
	);
}

function tariffAnswer(text: string): string {
	const catalog = tarifwerk();
	const area = detectArea(text);
	const tarifId = detectTariff(text);
	const areaName = area
		? (catalog.leistungsbereiche.find((b) => b.schluessel === area)?.name ?? area)
		: null;

	if (area) {
		const tariffs = tarifId
			? catalog.tarife.filter((t) => t.schluessel === tarifId)
			: catalog.tarife;
		const parts = tariffs.map((tariff) => {
			const benefit = tariff.leistungen?.[area];
			if (!benefit?.versichert) return `${tariff.anzeigename}: nicht versichert`;
			let detail = `${tariff.anzeigename}: ${benefit.quote} %`;
			if (benefit.limit_pro_jahr != null) detail += ` bis ${currency(benefit.limit_pro_jahr)}/Jahr`;
			if (benefit.limit_gesamt != null) detail += ` (gesamt max. ${currency(benefit.limit_gesamt)})`;
			if (benefit.max_faelle != null)
				detail += `, max. ${benefit.max_faelle} Fälle in ${benefit.zeitraum_jahre} Jahren`;
			return detail;
		});
		return `Zum Bereich ${areaName}: ${parts.join('. ')}. Die Prozentsätze verstehen sich inklusive der GKV-Vorleistung; in den ersten Jahren gilt zudem die Zahnstaffel.`;
	}

	if (tarifId) {
		const tariff = catalog.tarife.find((t) => t.schluessel === tarifId);
		if (tariff) {
			return (
				`${tariff.anzeigename}: ${tariff.positionierung}. ` +
				`Selbstbehalt ${tariff.selbstbehalt === 0 ? 'keiner' : currency(tariff.selbstbehalt) + ' pro Jahr'}, ` +
				`Wartezeit ${tariff.wartezeit_monate} Monate${tariff.wartezeit_entfaellt_bei_vorversicherung ? ' (entfällt bei lückenloser Vorversicherung)' : ''}. ` +
				'Fragen Sie gerne nach einem konkreten Leistungsbereich, z. B. Implantaten oder Prophylaxe.'
			);
		}
	}

	return (
		'Ich beantworte Fragen zu unseren vier Zahntarifen (smart, balance, brillant, brillant mit Selbstbehalt) ' +
		'und empfehle Ihnen einen passenden Tarif — z. B. „Was zahlt brillant bei Implantaten?" ' +
		'Eine Erstattungshöhe sage ich nicht zu; die klärt die Sachbearbeitung im Schadensfall.'
	);
}

function reimbursementBoundaryAnswer(text: string): string {
	const boundary =
		'Eine konkrete Erstattungshöhe sage ich nicht zu — sie hängt zusätzlich von Selbstbehalt, ' +
		'Zahnstaffel, Jahreshöchstgrenze und Ihrem bisherigen Verbrauch ab und wird erst im ' +
		'Schadensfall ermittelt. ';
	return boundary + tariffAnswer(text);
}

function answer(text: string, context: CustomerContext | undefined): string {
	const lower = text.toLowerCase();
	if (
		/mein(e|em|en)? (vertrag|tarif|fall|faelle|fälle|erstattung|schadensfall|schadensfaelle|schadensfälle)/.test(
			lower
		)
	) {
		return contractAnswer(context);
	}
	if (
		/(erstattung|back|zurück|bekomme|bekaeme|bekäme|rechnung)/.test(lower) &&
		/\d/.test(lower)
	) {
		return reimbursementBoundaryAnswer(lower);
	}
	if (/(empfehl|welcher tarif|passt zu mir|was soll ich)/.test(lower)) {
		return recommendationAnswer();
	}
	return tariffAnswer(lower);
}

const SCHADENSFALL_REFUSAL =
	'Sie möchten offenbar einen Leistungsfall klären — eine Rechnung einreichen, nach einer ' +
	'Erstattung fragen oder eine Abrechnung nachvollziehen. Das kann ich hier nicht: Für Belege ' +
	'gibt es in diesem Chat noch keinen Agenten, und ich habe weder Ihre Rechnungen noch Ihre ' +
	'Abrechnungen vorliegen.\n\n' +
	'Wenden Sie sich dafür bitte an die Sachbearbeitung von atra.dent. Was ich Ihnen stattdessen ' +
	'anbieten kann: eine Beratung dazu, was Ihr Tarif dem Grunde nach leistet und welche Grenzen ' +
	'dabei gelten.';

export class MockAgentClient implements AgentClient {
	async *stream(input: AgentInput): AsyncIterable<AgentEvent> {
		if (input.beleg) {
			await sleep(300);
			for (const word of SCHADENSFALL_REFUSAL.split(/(?<=\s)/)) {
				yield { type: 'text-delta', text: word };
				await sleep(15);
			}
			yield { type: 'done' };
			return;
		}

		yield { type: 'status', text: 'prüfe Tarifdaten …' };
		yield {
			type: 'trace',
			trace: {
				text: 'prüfe Tarifdaten',
				sender: 'chat-ui',
				protocol: 'INTERNAL',
				timestamp: new Date().toISOString(),
				operation: 'mock',
				data: {
					note:
						'AGENT_CLIENT=mock — die Antwort kommt aus dem Tarifwerk dieser Oberfläche. ' +
						'Kein Orchestrator, kein Beratungsagent, kein MCP-Server.',
					loggedIn: input.customerId !== undefined
				}
			}
		};
		const context = input.customerId
			? ((await readCustomerContext(input.customerId).catch(() => null)) ?? undefined)
			: undefined;
		await sleep(400);
		const question = input.history.at(-1)?.text ?? '';
		const response = answer(question, context);
		yield {
			type: 'trace',
			trace: {
				text: 'Antwort aus dem Tarifwerk',
				sender: 'chat-ui',
				protocol: 'INTERNAL',
				timestamp: new Date().toISOString(),
				operation: 'mock',
				data: { recordLoaded: context !== undefined }
			}
		};
		for (const word of response.split(/(?<=\s)/)) {
			yield { type: 'text-delta', text: word };
			await sleep(25);
		}
		yield { type: 'done' };
	}
}
