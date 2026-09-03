import type { Bewertungsempfehlung } from './api';

export type Outcome = 'geprueft' | 'laeuft' | 'weiterbearbeitet' | 'abgelehnt' | 'gestoert';

export interface PruefungResponse {
	outcome: Outcome;
	status?: string;
	text: string;
	empfehlung?: Bewertungsempfehlung;
}
