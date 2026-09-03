import { avatarUrl } from '$lib/server/avatar';
import { readKunde } from '$lib/server/kernsystem';

export async function load({ cookies }) {
	const id = Number(cookies.get('demo-kunde'));
	const customer = Number.isInteger(id) && id > 0 ? await readKunde(id).catch(() => null) : null;
	return {
		signedInCustomer: customer
			? {
					id: customer.id,
					vorname: customer.vorname,
					nachname: customer.nachname,
					avatar: avatarUrl(customer)
				}
			: null
	};
}
