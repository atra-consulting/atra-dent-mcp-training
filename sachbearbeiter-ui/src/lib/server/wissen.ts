import { env } from '$env/dynamic/private';
import { ApiError, type ProblemDetails, type TarifId } from '$lib/types/api';
import type {
	Dokument,
	GozPruefungRequest,
	GozPruefungResult,
	SucheRequest,
	SucheResult,
	Vertraulichkeit
} from '$lib/types/wissen';

export const wissenBase = () => env.WISSEN_URL ?? 'http://localhost:8082/api/v1';

async function httpRequest<T>(path: string, init?: RequestInit): Promise<T | null> {
	const response = await fetch(`${wissenBase()}${path}`, {
		...init,
		headers: { 'Content-Type': 'application/json', ...init?.headers }
	});
	if (response.status === 404) return null;
	if (!response.ok) {
		const problem = (await response.json().catch(() => null)) as ProblemDetails | null;
		throw new ApiError(
			problem ?? {
				title: `Wissensdienst antwortete mit ${response.status}`,
				status: response.status
			}
		);
	}
	return (await response.json()) as T;
}

export async function listDocuments(filter?: {
	tarif?: TarifId;
	vertraulichkeit?: Vertraulichkeit;
}): Promise<Dokument[]> {
	const query = new URLSearchParams();
	if (filter?.tarif) query.set('tarif', filter.tarif);
	if (filter?.vertraulichkeit) query.set('vertraulichkeit', filter.vertraulichkeit);
	const suffix = query.size > 0 ? `?${query}` : '';
	return (await httpRequest<Dokument[]>(`/dokumente${suffix}`)) ?? [];
}

export async function searchBedingungswerk(request: SucheRequest): Promise<SucheResult> {
	const result = await httpRequest<SucheResult>('/bedingungen/suche', {
		method: 'POST',
		body: JSON.stringify(request)
	});
	if (!result)
		throw new ApiError({ title: 'Unerwartete Antwort der Bedingungssuche', status: 500 });
	return result;
}

export async function checkGoz(request: GozPruefungRequest): Promise<GozPruefungResult> {
	const result = await httpRequest<GozPruefungResult>('/goz/pruefung', {
		method: 'POST',
		body: JSON.stringify(request)
	});
	if (!result) throw new ApiError({ title: 'Unerwartete Antwort der GOZ-Prüfung', status: 500 });
	return result;
}
