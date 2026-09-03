import type { Ablehnungsgrund, GozZustand, Schadensfallstatus } from '$lib/types/api';

const euroFormat = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
const dateFormat = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium' });
const dateTimeFormat = new Intl.DateTimeFormat('de-DE', {
	dateStyle: 'medium',
	timeStyle: 'short'
});

const schadensfallstatusLabels: Record<Schadensfallstatus, string> = {
	eingereicht: 'Eingereicht',
	in_pruefung: 'In Prüfung',
	geprueft_freigabe: 'Geprüft – Freigabe empfohlen',
	geprueft_eskalation: 'Geprüft – Eskalation',
	genehmigt: 'Genehmigt',
	abgelehnt: 'Abgelehnt',
	ausgezahlt: 'Ausgezahlt'
};

export function schadensfallstatus(status: Schadensfallstatus | string): string {
	return schadensfallstatusLabels[status as Schadensfallstatus] ?? status;
}

export type BadgeVariant = 'default' | 'secondary' | 'outline' | 'destructive';

const schadensfallstatusVariants: Record<Schadensfallstatus, BadgeVariant> = {
	eingereicht: 'outline',
	in_pruefung: 'secondary',
	geprueft_freigabe: 'default',
	geprueft_eskalation: 'secondary',
	genehmigt: 'default',
	abgelehnt: 'destructive',
	ausgezahlt: 'default'
};

export function schadensfallstatusVariant(status: string): BadgeVariant {
	return schadensfallstatusVariants[status as Schadensfallstatus] ?? 'outline';
}

export function dateTime(iso: string | null | undefined): string {
	if (!iso) return '—';
	const value = new Date(iso);
	if (Number.isNaN(value.getTime())) return '—';
	return dateTimeFormat.format(value);
}

const akteurLabels: Record<string, string> = {
	kunde: 'Kunde',
	agent: 'Agent',
	sachbearbeitung: 'Sachbearbeitung',
	system: 'System'
};

export function akteurLabel(akteur: string): string {
	return akteurLabels[akteur] ?? akteur;
}

const eskalationsgrundLabels: Record<string, string> = {
	GOZ_UNCLEAR: 'GOZ-Zuordnung unklar',
	BETRAG_DEVIATES: 'Betrag weicht von der Berechnung ab',
	ARZT_FLAGGED: 'Arztauskunft auffällig',
	ARZT_UNAVAILABLE: 'Arztauskunft nicht verfügbar',
	BETRAG_ABOVE_THRESHOLD: 'Betrag über Freigabeschwelle',
	VERTRAG_INACTIVE: 'Vertrag inaktiv',
	WARTEZEIT: 'Wartezeit läuft',
	PATIENT_UNCLEAR: 'Patient stimmt nicht mit Kunde überein',
	DUPLICATE: 'Mögliches Duplikat',
	EXTRACTION_INCOMPLETE: 'Extraktion unvollständig',
	MODELL_WITHOUT_RESULT: 'Modell ohne Ergebnis',
	MODELL_ESCALATION: 'Eskalation auf Einschätzung des Agenten'
};

export function eskalationsgrund(code: string): string {
	return eskalationsgrundLabels[code] ?? code;
}

export function euro(geldbetrag: string | number | null | undefined): string {
	if (geldbetrag === null || geldbetrag === undefined || geldbetrag === '') return '—';
	const value = typeof geldbetrag === 'number' ? geldbetrag : Number.parseFloat(geldbetrag);
	if (Number.isNaN(value)) return '—';
	return euroFormat.format(value);
}

export function geldbetragFromInput(input: string): string | null {
	const cleaned = input.trim().replace(/[\s€]/g, '');
	if (!cleaned) return null;
	let normalized: string;
	if (/^\d{1,3}(\.\d{3})+(,\d{1,2})?$/.test(cleaned)) {
		normalized = cleaned.replace(/\./g, '').replace(',', '.');
	} else {
		normalized = cleaned.replace(',', '.');
	}
	if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
	return Number.parseFloat(normalized).toFixed(2);
}

export function date(iso: string | null | undefined): string {
	if (!iso) return '—';
	const value = new Date(iso);
	if (Number.isNaN(value.getTime())) return '—';
	return dateFormat.format(value);
}

const gozZustandLabels: Record<GozZustand, string> = {
	ENTHALTEN: 'Versichert',
	NICHT_ENTHALTEN: 'Nicht versichert',
	NICHT_BESTIMMBAR: 'Nicht bestimmbar',
	UNBEKANNT: 'Unbekannt'
};

export function gozZustand(zustand: GozZustand | string): string {
	return gozZustandLabels[zustand as GozZustand] ?? zustand;
}

const ablehnungsgrundLabels: Record<Ablehnungsgrund, string> = {
	ANGERATEN: 'Bereits angeraten/begonnen',
	KOSMETIK: 'Kosmetisch',
	NICHT_APPROBIERT: 'Behandler nicht approbiert',
	FEHLENDE_ZAEHNE: 'Fehlende Zähne bei Antrag',
	NICHT_VERSICHERT: 'Leistungsbereich nicht versichert',
	WARTEZEIT: 'Wartezeit läuft',
	SONSTIGES: 'Sonstiges'
};

export function ablehnungsgrund(value: Ablehnungsgrund | string): string {
	return ablehnungsgrundLabels[value as Ablehnungsgrund] ?? value;
}
