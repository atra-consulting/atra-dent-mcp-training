import type { RequestHandler } from './$types';
import { CONVERSATION_COOKIE } from '$lib/server/conversation';

export const POST: RequestHandler = async ({ cookies }) => {
	cookies.delete(CONVERSATION_COOKIE, { path: '/' });
	return new Response(null, { status: 204 });
};
