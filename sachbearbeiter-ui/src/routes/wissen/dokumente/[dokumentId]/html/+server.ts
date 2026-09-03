import { error } from '@sveltejs/kit';
import { wissenBase } from '$lib/server/wissen';

export async function GET({ params }) {
	const response = await fetch(`${wissenBase()}/dokumente/${params.dokumentId}/html`).catch(
		() => null
	);
	if (!response) error(502, 'Wissensdienst nicht erreichbar');
	if (response.status === 404) error(404, 'Dokument nicht gefunden');
	if (!response.ok) error(502, 'Wissensdienst nicht erreichbar');
	return new Response(response.body, {
		headers: { 'Content-Type': response.headers.get('content-type') ?? 'text/html; charset=utf-8' }
	});
}
