import type { Geldbetrag, LeistungsbereichSchluessel, TarifId } from './api';

export type Dokumentart =
	'BEDINGUNGSWERK' | 'TARIFVERGLEICH' | 'GOZ_ZUORDNUNG' | 'BERATUNGSHANDBUCH';

export type Vertraulichkeit = 'OEFFENTLICH' | 'INTERN';

export interface Dokument {
	dokumentId: string;
	titel: string;
	art: Dokumentart;
	vertraulichkeit: Vertraulichkeit;
	tarif?: TarifId | null;
	pdfUrl: string;
	htmlUrl: string;
}

export interface SucheRequest {
	tarif: TarifId;
	frage: string;
	anzahl?: number;
}

export interface Fundstelle {
	dokumentId: string;
	abschnittId: string;
	ueberschrift: string;
	text: string;
	bewertung: number;
	htmlUrl: string;
	pdfUrl: string;
}

export interface SucheResult {
	tarif: TarifId;
	frage: string;
	treffer: Fundstelle[];
}

export interface GozPruefungRequest {
	tarif: TarifId;
	nummern: string[];
}

export type Gozstatus = 'ENTHALTEN' | 'NICHT_ENTHALTEN' | 'NICHT_BESTIMMBAR' | 'UNBEKANNT';

export interface Leistungsgrenzen {
	limitProJahr?: Geldbetrag;
	limitGesamt?: Geldbetrag;
	faelleProJahr?: number;
	maxFaelle?: number;
	zeitraumJahre?: number;
	wartezeitMonate?: number;
	bedingung?: string;
}

export interface GozBefund {
	nummer: string;
	bezeichnung?: string;
	abschnitt?: string;
	leistungsbereich?: LeistungsbereichSchluessel | null;
	status: Gozstatus;
	quote?: number | null;
	grenzen?: Leistungsgrenzen | null;
	begruendung: string;
}

export interface GozPruefungResult {
	tarif: TarifId;
	befunde: GozBefund[];
}
