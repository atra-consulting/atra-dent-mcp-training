import type {
	BeitragsberechnungRequest,
	BeitragsberechnungResult,
	ErstattungsberechnungRequest,
	ErstattungsberechnungResult,
	Kunde,
	KundeAendern,
	KundeSchreiben,
	Schaden,
	Schadensfall,
	SchadensfallAendern,
	Schadensfallstatus,
	TarifDto
} from '$lib/types/api';

export interface BackofficeApi {
	listKunden(): Promise<Kunde[]>;
	readKunde(id: number): Promise<Kunde | null>;
	createKunde(data: KundeSchreiben): Promise<Kunde>;
	updateKunde(id: number, data: KundeAendern): Promise<Kunde | null>;
	listKundenSchadensfaelle(kundenId: number): Promise<Schadensfall[] | null>;
	listSchadensfaelle(filter?: {
		status?: Schadensfallstatus[];
		kundenId?: number;
	}): Promise<Schadensfall[]>;
	readSchadensfall(id: number): Promise<Schadensfall | null>;
	submitSchadensfall(data: Schaden): Promise<Schadensfall>;
	updateSchadensfall(id: number, data: SchadensfallAendern): Promise<Schadensfall | null>;
	listTarife(): Promise<TarifDto[]>;
	calculateBeitrag(request: BeitragsberechnungRequest): Promise<BeitragsberechnungResult>;
	calculateErstattung(request: ErstattungsberechnungRequest): Promise<ErstattungsberechnungResult>;
}
