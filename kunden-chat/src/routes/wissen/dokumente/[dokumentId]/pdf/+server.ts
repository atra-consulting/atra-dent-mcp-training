import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import type { RequestHandler } from './$types';


const base = () => env.WISSEN_URL ?? 'http://localhost:8082/api/v1';

const ID_PATTERN = /^[a-z0-9-]{1,64}$/;

export const GET: RequestHandler = async ({ params, fetch }) => {
	if (!ID_PATTERN.test(params.dokumentId)) {
		error(400, 'Keine gültige Dokumentkennung');
	}

	const response = await fetch(`${base()}/dokumente/${params.dokumentId}/pdf`);
	if (!response.ok) {
		error(response.status === 404 ? 404 : 502, 'Das Bedingungswerk ist nicht verfügbar');
	}

	return new Response(response.body, {
		headers: {
			'content-type': response.headers.get('content-type') ?? 'application/pdf',
			'content-disposition': 'inline'
		}
	});
};
