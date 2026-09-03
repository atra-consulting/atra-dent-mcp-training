
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
export type Bewertungsempfehlung = 'freigabe' | 'eskalation';
export type GozZustand = 'ENTHALTEN' | 'NICHT_ENTHALTEN' | 'NICHT_BESTIMMBAR' | 'UNBEKANNT';
export type Protokollakteur = 'kunde' | 'agent' | 'sachbearbeitung' | 'system';
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

export interface Rechnungskopf {
	rechnungsnummer: string | null;
	rechnungsdatum: string | null;
	absender: string | null;
	patient: string | null;
	gesamtbetrag: Geldbetrag | null;
}

export interface Eskalationsgrund {
	code: string;
	text: string;
}

export interface Bewertungsposition {
	index?: number | null;
	goz: string | null;
	leistungsbereich: LeistungsbereichSchluessel | null;
	zustand: GozZustand;
	begruendung: string;
}

export interface Arztauskunft {
	plausibilitaet: 'plausibel' | 'auffaellig';
	notwendigkeit: 'ueblich' | 'fraglich';
	text: string;
	hinweis: string;
}

export interface Bewertung {
	empfehlung: Bewertungsempfehlung;
	erstattungsvorschlag: Geldbetrag | null;
	eskalationsgruende: Eskalationsgrund[];
	positionen: Bewertungsposition[];
	arztauskunft: Arztauskunft | null;
	begruendung: string;
	agent: string;
	modell: string | null;
	zeitpunkt: string;
}

export interface AgentCall {
	zeitpunkt: string;
	art: 'modell' | 'tool' | 'a2a';
	name: string;
	eingabeKurz?: string | null;
	ergebnisKurz?: string | null;
	dauerMs?: number | null;
}

export interface Protokolleintrag {
	zeitpunkt: string;
	akteur: Protokollakteur;
	schritt: string;
	detail?: string | null;
	calls?: AgentCall[];
}

export interface Schaden {
	kundenId: number;
	behandlungsdatum: string;
	positionen: Schadenposition[];
	rechnung?: Rechnungskopf | null;
}

export interface SchadensfallAendern {
	status?: Schadensfallstatus;
	erstattungsbetrag?: Geldbetrag;
	ablehnungsgrund?: Ablehnungsgrund;
	ablehnungshinweis?: string;
	akteur?: Protokollakteur;
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
	rechnung: Rechnungskopf | null;
	bewertung: Bewertung | null;
	bearbeitungsprotokoll: Protokolleintrag[];
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

export type Rechenschritt =
	'ERSTATTUNGSFAEHIG' | 'QUOTE' | 'SUBLIMIT' | 'GKV' | 'SELBSTBEHALT' | 'STAFFEL';

export interface Vorverbrauch {
	staffel?: Geldbetrag;
	selbstbehalt?: Geldbetrag;
	year?: Geldbetrag;
	leistungsbereiche?: Partial<Record<LeistungsbereichSchluessel, Geldbetrag>>;
	leistungsbereicheSeitVersicherungsbeginn?: Partial<
		Record<LeistungsbereichSchluessel, Geldbetrag>
	>;
}

export interface ErstattungsberechnungRequest {
	tarifId: TarifId;
	versicherungsbeginn: string;
	behandlungsdatum: string;
	positionen: Schadenposition[];
	gkvLeistung?: Geldbetrag;
	unfallbedingt?: boolean;
	verbrauch?: Vorverbrauch;
}

export interface RechenschrittResult {
	schritt: Rechenschritt;
	betrag: Geldbetrag;
	erlaeuterung?: string;
}

export interface ErstattungsberechnungResult {
	erstattungsbetrag: Geldbetrag;
	rechnungsbetrag: Geldbetrag;
	eigenanteil: Geldbetrag;
	versicherungsjahr: number;
	schritte: RechenschrittResult[];
}
