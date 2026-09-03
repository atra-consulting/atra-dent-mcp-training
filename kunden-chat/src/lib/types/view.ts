
import type { Schadensfallstatus } from './api';

export type Geldbetrag = string | number;

export type ViewKind =
	| 'tarifvergleich'
	| 'beitragsvergleich'
	| 'tarifempfehlung'
	| 'vertrag'
	| 'kontaktdaten'
	| 'schadensfaelle'
	| 'hymne';

export interface Vertragstarif {
	schluessel?: string;
	anzeigename?: string | null;
}

export interface Leistung {
	versichert?: boolean;
	quote?: number | null;
	limitProJahr?: Geldbetrag | null;
	limitGesamt?: Geldbetrag | null;
	faelleProJahr?: number | null;
	maxFaelle?: number | null;
	zeitraumJahre?: number | null;
	wartezeitMonate?: number | null;
	bedingung?: string | null;
}

export interface Staffelstufe {
	bisJahr?: number;
	betrag?: Geldbetrag;
}

export interface Vergleichstarif {
	schluessel?: string;
	anzeigename?: string;
	positionierung?: string | null;
	eintrittsalter?: { from?: number | null; to?: number | null } | null;
	selbstbehalt?: Geldbetrag | null;
	jahreshoechstgrenze?: Geldbetrag | null;
	wartezeitMonate?: number | null;
	wartezeitEntfaelltBeiVorversicherung?: boolean | null;
	zahnstaffel?: Staffelstufe[] | null;
	bedingungswerk?: string | null;
}

export interface Bereichsvergleich {
	schluessel?: string;
	name?: string;
	beschreibung?: string | null;
	leistungen?: Record<string, Leistung>;
}

export interface TarifvergleichView {
	kind: 'tarifvergleich';
	stand?: string;
	tarife?: Vergleichstarif[];
	leistungsbereiche?: Bereichsvergleich[];
	vertragstarif?: Vertragstarif;
}

export interface Beitrag {
	tarif?: string;
	anzeigename?: string | null;
	monatsbeitrag?: Geldbetrag;
}

export interface BeitragsvergleichView {
	kind: 'beitragsvergleich';
	beitraege?: Beitrag[];
	vertragstarif?: Vertragstarif;
}

export interface Empfehlung {
	rang?: number;
	tarif?: string;
	anzeigename?: string | null;
}

export interface TarifempfehlungView {
	kind: 'tarifempfehlung';
	empfehlungen?: Empfehlung[];
	vertragstarif?: Vertragstarif;
}

export interface VertragView {
	kind: 'vertrag';
	vorname?: string | null;
	tarif?: string | null;
	anzeigename?: string | null;
	versicherungsbeginn?: string | null;
	status?: string | null;
	vorversicherung?: boolean | null;
	fehlendeZaehne?: number | null;
}

export interface Anschrift {
	strasse?: string | null;
	plz?: string | null;
	ort?: string | null;
	land?: string | null;
}

export interface KontaktdatenView {
	kind: 'kontaktdaten';
	vorname?: string | null;
	email?: string | null;
	telefon?: string | null;
	adresse?: Anschrift | null;
}

export interface Schadensfall {
	id?: number;
	behandlungsdatum?: string | null;
	rechnungsbetrag?: Geldbetrag | null;
	erstattungsbetrag?: Geldbetrag | null;
	erstattungsvorschlag?: Geldbetrag | null;
	empfehlung?: 'freigabe' | 'eskalation' | null;
	status?: Schadensfallstatus | string | null;
	ablehnungsgrund?: string | null;
	ablehnungshinweis?: string | null;
}

export interface SchadensfaelleView {
	kind: 'schadensfaelle';
	faelle?: Schadensfall[];
}

export const STAGE_LABELS = [
	'Eingereicht',
	'In Prüfung',
	'Geprüft',
	'Genehmigt',
	'Ausgezahlt'
] as const;

const STAGES: Record<
	string,
	{ stage: 0 | 1 | 2 | 3 | 4; label: string; rejected?: boolean; atSachbearbeitung?: boolean }
> = {
	eingereicht: { stage: 0, label: 'Eingereicht' },
	in_pruefung: { stage: 1, label: 'In Prüfung' },
	geprueft_freigabe: { stage: 2, label: 'Geprüft' },
	geprueft_eskalation: {
		stage: 2,
		label: 'Liegt bei der Sachbearbeitung',
		atSachbearbeitung: true
	},
	genehmigt: { stage: 3, label: 'Genehmigt' },
	abgelehnt: { stage: 2, label: 'Abgelehnt', rejected: true },
	ausgezahlt: { stage: 4, label: 'Ausgezahlt' }
};

export function statusStage(status: string | undefined): {
	stage: 0 | 1 | 2 | 3 | 4;
	label: string;
	rejected: boolean;
	atSachbearbeitung: boolean;
} {
	const match = status && Object.hasOwn(STAGES, status) ? STAGES[status] : undefined;
	return {
		stage: match?.stage ?? 0,
		label: match?.label ?? status ?? '—',
		rejected: match?.rejected ?? false,
		atSachbearbeitung: match?.atSachbearbeitung ?? false
	};
}

export function statusNote(status: string | undefined): string | null {
	if (!status) return null;
	const current = statusStage(status);
	return current.label === STAGE_LABELS[current.stage] ? null : current.label;
}

const ABLEHNUNGSGRUENDE: Record<string, string> = {
	ANGERATEN: 'Behandlung bereits angeraten/begonnen',
	KOSMETIK: 'Kosmetische Leistung',
	NICHT_APPROBIERT: 'Behandler nicht approbiert',
	FEHLENDE_ZAEHNE: 'Fehlende Zähne bei Antragstellung',
	NICHT_VERSICHERT: 'Leistungsbereich nicht versichert',
	WARTEZEIT: 'Wartezeit noch nicht abgelaufen',
	SONSTIGES: 'Sonstiger Grund'
};

export function ablehnungsgrundLabel(code: string | null | undefined): string {
	if (code && Object.hasOwn(ABLEHNUNGSGRUENDE, code)) {
		return ABLEHNUNGSGRUENDE[code];
	}
	return 'Abgelehnt';
}

export interface HymneView {
	kind: 'hymne';
}

export type View =
	| TarifvergleichView
	| BeitragsvergleichView
	| TarifempfehlungView
	| VertragView
	| KontaktdatenView
	| SchadensfaelleView
	| HymneView;

const KINDS: ReadonlySet<string> = new Set<ViewKind>([
	'tarifvergleich',
	'beitragsvergleich',
	'tarifempfehlung',
	'vertrag',
	'kontaktdaten',
	'schadensfaelle',
	'hymne'
]);

export function isView(value: unknown): value is View {
	if (typeof value !== 'object' || value === null) return false;
	const kind = (value as { kind?: unknown }).kind;
	return typeof kind === 'string' && KINDS.has(kind);
}

export const MAX_VIEWS = 2;

export function viewsFrom(value: unknown): View[] {
	if (!Array.isArray(value)) return [];
	return value.filter(isView).slice(0, MAX_VIEWS);
}
