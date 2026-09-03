import type { LeistungsbereichSchluessel, TarifId } from './api';

export interface TarifLeistung {
	versichert: boolean;
	quote?: number;
	limit_pro_jahr?: number;
	faelle_pro_jahr?: number | null;
	limit_gesamt?: number;
	max_faelle?: number;
	zeitraum_jahre?: number;
	wartezeit_monate?: number;
	bedingung?: string;
}

export interface TarifModell {
	schluessel: TarifId;
	anzeigename: string;
	positionierung: string;
	eintrittsalter: { von: number; bis: number };
	selbstbehalt: number;
	jahreshoechstgrenze: number | null;
	wartezeit_monate: number;
	wartezeit_entfaellt_bei_vorversicherung?: boolean;
	staffel: string;
	leistungen?: Partial<Record<LeistungsbereichSchluessel, TarifLeistung>>;
	leistungen_wie?: TarifId;
}

export interface Staffel {
	schluessel: string;
	dauer_jahre: number;
	hinweis?: string;
	stufen: { bis_jahr: number | null; betrag: number | null }[];
}

export interface LeistungsbereichInfo {
	schluessel: LeistungsbereichSchluessel;
	name: string;
	beschreibung?: string;
}

export interface Tarifwerk {
	stand: string;
	leistungsbereiche: LeistungsbereichInfo[];
	tarife: TarifModell[];
	staffeln: Staffel[];
	ausschluesse: {
		schluessel: string;
		text: string;
		ausnahme?: { tarife: string[]; text: string };
	}[];
}
