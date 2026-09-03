
export type TarifId = 'ATRA_DENT_S' | 'ATRA_DENT_B' | 'ATRA_DENT_X' | 'ATRA_DENT_X_SB';
export type Kundenstatus = 'aktiv' | 'inaktiv';
export type Schadensfallstatus =
	| 'eingereicht'
	| 'in_pruefung'
	| 'geprueft_freigabe'
	| 'geprueft_eskalation'
	| 'genehmigt'
	| 'abgelehnt'
	| 'ausgezahlt';
export type Ablehnungsgrund =
	| 'ANGERATEN'
	| 'KOSMETIK'
	| 'NICHT_APPROBIERT'
	| 'FEHLENDE_ZAEHNE'
	| 'NICHT_VERSICHERT'
	| 'WARTEZEIT'
	| 'SONSTIGES';
export type LeistungsbereichSchluessel =
	'ZE' | 'IMP' | 'INL' | 'ZERH' | 'PAR' | 'PZR' | 'KFO' | 'FUN' | 'NAR' | 'AKUT';

export type Geldbetrag = string;

export interface Adresse {
	strasse: string;
	plz: string;
	ort: string;
	land: string;
}

export interface KundeSchreiben {
	vorname: string;
	nachname: string;
	geburtsdatum: string;
	email: string;
	telefon: string;
	adresse: Adresse;
	tarifId: TarifId;
	versicherungsbeginn: string;
	vorversicherung: boolean;
	fehlendeZaehne: number;
	status?: Kundenstatus;
}

export type KundeAendern = Partial<KundeSchreiben>;

export interface Kunde extends KundeSchreiben {
	id: number;
	erstelltAm?: string;
	geaendertAm?: string;
}

export interface TarifDto {
	id: TarifId;
	name: string;
	basisbeitragMonatlich: Geldbetrag;
}

export interface BeitragsberechnungRequest {
	geburtsdatum: string;
	tarifId: TarifId;
	gewuenschterBeginn: string;
	kundenId?: number;
}

export interface BeitragsberechnungResult {
	monatsbeitrag: Geldbetrag;
}

export interface Schadenposition {
	goz: string | null;
	leistungsbereich: LeistungsbereichSchluessel | null;
	betrag: Geldbetrag;
	beschreibung: string;
	zahn?: string | null;
	datum?: string | null;
	anzahl?: number | null;
}

export interface Schaden {
	kundenId: number;
	behandlungsdatum: string;
	positionen: Schadenposition[];
}

export interface SchadensfallAendern {
	status?: Schadensfallstatus;
	erstattungsbetrag?: Geldbetrag;
	ablehnungsgrund?: Ablehnungsgrund;
	ablehnungshinweis?: string;
}

export interface Schadensfall {
	id: number;
	kundenId: number;
	behandlungsdatum: string;
	positionen: Schadenposition[];
	rechnungsbetrag: Geldbetrag;
	erstattungsbetrag: Geldbetrag | null;
	status: Schadensfallstatus;
	ablehnungsgrund: Ablehnungsgrund | null;
	ablehnungshinweis: string | null;
	eingereichtAm: string;
}

export interface ExtrahierterPatient {
	name: string;
	strasse: string;
	plz: string;
	ort: string;
}

export interface ExtrahierteRechnungsposition {
	datum: string | null;
	ziffer: string | null;
	leistung: string;
	zahn: string | null;
	anzahl: number | null;
	faktor: string | null;
	betrag: Geldbetrag;
}

export interface RechnungsextraktionResult {
	rechnungsnummer: string;
	rechnungsdatum: string;
	absender: string;
	patient: ExtrahierterPatient;
	positionen: ExtrahierteRechnungsposition[];
	gesamtbetrag: Geldbetrag;
	zahlbetrag: Geldbetrag;
}

export interface ProblemDetails {
	type?: string;
	title: string;
	status: number;
	detail?: string;
	instance?: string;
}

export class ApiError extends Error {
	constructor(public problem: ProblemDetails) {
		super(problem.detail ?? problem.title);
	}
}
