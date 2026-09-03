import { env } from '$env/dynamic/private';
import {
	ApiError,
	type Kunde,
	type ProblemDetails,
	type RechnungsextraktionResult,
	type Schadensfall
} from '$lib/types/api';
import type { CustomerContext } from '$lib/types/chat';

const base = () => env.KERNSYSTEM_URL ?? 'http://localhost:8080/api/v1';
const apiKey = () => env.KERNSYSTEM_API_KEY ?? 'atra-lab-2026';

async function request<T>(path: string): Promise<T | null> {
	const response = await fetch(`${base()}${path}`, { headers: { 'X-API-Key': apiKey() } });
	if (response.status === 404) return null;
	if (!response.ok) {
		const problem = (await response.json().catch(() => null)) as ProblemDetails | null;
		throw new ApiError(
			problem ?? { title: `Kernsystem antwortete mit ${response.status}`, status: response.status }
		);
	}
	return (await response.json()) as T;
}

export async function listKunden(): Promise<Kunde[]> {
	return (await request<Kunde[]>('/kunden')) ?? [];
}

export function readKunde(id: number): Promise<Kunde | null> {
	return request<Kunde>(`/kunden/${id}`);
}

export class BelegNotReadableError extends Error {
	constructor(public problem: ProblemDetails | null) {
		super('Das Kernsystem konnte den Beleg nicht auswerten');
	}
}

export async function extractRechnung(
	body: ReadableStream<Uint8Array>
): Promise<RechnungsextraktionResult> {
	const response = await fetch(`${base()}/rechnungsextraktion`, {
		method: 'POST',
		headers: { 'X-API-Key': apiKey(), 'Content-Type': 'application/pdf' },
		body: body,
		// @ts-expect-error duplex gehoert zu undici und fehlt in den DOM-Typen
		duplex: 'half'
	});

	if (response.ok) return (await response.json()) as RechnungsextraktionResult;

	const problem = (await response.json().catch(() => null)) as ProblemDetails | null;
	if (response.status === 400) throw new BelegNotReadableError(problem);
	throw new ApiError(
		problem ?? { title: `Kernsystem antwortete mit ${response.status}`, status: response.status }
	);
}

export async function readCustomerContext(id: number): Promise<CustomerContext | null> {
	const [customer, claims] = await Promise.all([
		request<Kunde>(`/kunden/${id}`),
		request<Schadensfall[]>(`/kunden/${id}/schadensfaelle`)
	]);
	if (!customer) return null;
	return { customer, claims: claims ?? [] };
}
