import { redirect } from '@sveltejs/kit';
import { avatarUrl } from '$lib/server/avatar';
import { CONVERSATION_COOKIE } from '$lib/server/conversation';
import { readKunde, listKunden } from '$lib/server/kernsystem';

export async function load() {
	try {
		const customers = await listKunden();
		return {
			selection: customers.map((c) => ({
				id: c.id,
				name: `${c.vorname} ${c.nachname}`,
				tarifId: c.tarifId,
				avatar: avatarUrl(c)
			})),
			coreSystemError: false
		};
	} catch {
		return { selection: [], coreSystemError: true };
	}
}

export const actions = {
	anmelden: async ({ request, cookies }) => {
		const formData = await request.formData();
		const id = Number(formData.get('kundenId'));
		if (Number.isInteger(id) && id > 0 && (await readKunde(id).catch(() => null))) {
			cookies.set('demo-kunde', String(id), { path: '/', httpOnly: true, sameSite: 'lax' });
		}
		cookies.delete(CONVERSATION_COOKIE, { path: '/' });
		redirect(303, '/');
	},
	abmelden: async ({ cookies }) => {
		cookies.delete('demo-kunde', { path: '/' });
		cookies.delete(CONVERSATION_COOKIE, { path: '/' });
		redirect(303, '/');
	}
};
