import { env } from '$env/dynamic/private';
import type {
	BeitragsberechnungRequest,
	BeitragsberechnungResult,
	ErstattungsberechnungRequest,
	ErstattungsberechnungResult,
	Kunde,
	KundeAendern,
	KundeSchreiben,
	ProblemDetails,
	Schaden,
	Schadensfall,
	SchadensfallAendern,
	Schadensfallstatus,
	TarifDto
} from '$lib/types/api';
import { ApiError } from '$lib/types/api';
import type { BackofficeApi } from './api';

export class RestBackofficeApi implements BackofficeApi {
	#base = env.KERNSYSTEM_URL ?? 'http://localhost:8080/api/v1';
	#apiKey = env.KERNSYSTEM_API_KEY ?? 'atra-lab-2026';
	#rechenkernBase = env.RECHENKERN_URL ?? 'http://localhost:8086/api/v1';

	async #request<T>(path: string, init?: RequestInit): Promise<T | null> {
		const response = await fetch(`${this.#base}${path}`, {
			...init,
			headers: {
				'x-api-key': this.#apiKey,
				'Content-Type': 'application/json',
				...init?.headers
			}
		});
		if (response.status === 404) return null;
		if (!response.ok) {
			const problem = (await response.json().catch(() => null)) as ProblemDetails | null;
			throw new ApiError(
				problem ?? {
					title: `Kernsystem antwortete mit ${response.status}`,
					status: response.status
				}
			);
		}
		return (await response.json()) as T;
	}

	async #requestRechenkern<T>(path: string, init?: RequestInit): Promise<T | null> {
		const response = await fetch(`${this.#rechenkernBase}${path}`, {
			...init,
			headers: { 'Content-Type': 'application/json', ...init?.headers }
		});
		if (response.status === 404) return null;
		if (!response.ok) {
			const problem = (await response.json().catch(() => null)) as ProblemDetails | null;
			throw new ApiError(
				problem ?? {
					title: `Rechenkern antwortete mit ${response.status}`,
					status: response.status
				}
			);
		}
		return (await response.json()) as T;
	}

	async listKunden(): Promise<Kunde[]> {
		return (await this.#request<Kunde[]>('/kunden')) ?? [];
	}

	readKunde(id: number): Promise<Kunde | null> {
		return this.#request<Kunde>(`/kunden/${id}`);
	}

	async createKunde(data: KundeSchreiben): Promise<Kunde> {
		const kunde = await this.#request<Kunde>('/kunden', {
			method: 'POST',
			body: JSON.stringify(data)
		});
		if (!kunde) throw new ApiError({ title: 'Unerwartete Antwort beim Anlegen', status: 500 });
		return kunde;
	}

	updateKunde(id: number, data: KundeAendern): Promise<Kunde | null> {
		return this.#request<Kunde>(`/kunden/${id}`, { method: 'PATCH', body: JSON.stringify(data) });
	}

	listKundenSchadensfaelle(kundenId: number): Promise<Schadensfall[] | null> {
		return this.#request<Schadensfall[]>(`/kunden/${kundenId}/schadensfaelle`);
	}

	async listSchadensfaelle(filter?: {
		status?: Schadensfallstatus[];
		kundenId?: number;
	}): Promise<Schadensfall[]> {
		const params = new URLSearchParams();
		if (filter?.status?.length) params.set('status', filter.status.join(','));
		if (filter?.kundenId != null) params.set('kundenId', String(filter.kundenId));
		const query = params.size ? `?${params}` : '';
		return (await this.#request<Schadensfall[]>(`/schadensfaelle${query}`)) ?? [];
	}

	async readSchadensfall(id: number): Promise<Schadensfall | null> {
		return this.#request<Schadensfall>(`/schadensfaelle/${id}`);
	}

	async submitSchadensfall(data: Schaden): Promise<Schadensfall> {
		const schadensfall = await this.#request<Schadensfall>('/schadensfaelle', {
			method: 'POST',
			body: JSON.stringify(data)
		});
		if (!schadensfall)
			throw new ApiError({ title: 'Unerwartete Antwort beim Einreichen', status: 500 });
		return schadensfall;
	}

	updateSchadensfall(id: number, data: SchadensfallAendern): Promise<Schadensfall | null> {
		return this.#request<Schadensfall>(`/schadensfaelle/${id}`, {
			method: 'PATCH',
			body: JSON.stringify(data)
		});
	}

	async listTarife(): Promise<TarifDto[]> {
		return (await this.#requestRechenkern<TarifDto[]>('/tarife')) ?? [];
	}

	async calculateBeitrag(request: BeitragsberechnungRequest): Promise<BeitragsberechnungResult> {
		const result = await this.#requestRechenkern<BeitragsberechnungResult>('/beitragsberechnung', {
			method: 'POST',
			body: JSON.stringify(request)
		});
		if (!result)
			throw new ApiError({ title: 'Unerwartete Antwort der Beitragsberechnung', status: 500 });
		return result;
	}

	async calculateErstattung(
		request: ErstattungsberechnungRequest
	): Promise<ErstattungsberechnungResult> {
		const result = await this.#requestRechenkern<ErstattungsberechnungResult>(
			'/erstattungsberechnung',
			{
				method: 'POST',
				body: JSON.stringify(request)
			}
		);
		if (!result)
			throw new ApiError({ title: 'Unerwartete Antwort der Erstattungsberechnung', status: 500 });
		return result;
	}
}
