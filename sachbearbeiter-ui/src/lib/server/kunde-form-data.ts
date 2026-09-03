import type { KundeSchreiben, Kundenstatus, TarifId } from '$lib/types/api';

export function kundeFromForm(formData: FormData): KundeSchreiben {
	return {
		vorname: String(formData.get('vorname') ?? ''),
		nachname: String(formData.get('nachname') ?? ''),
		geburtsdatum: String(formData.get('geburtsdatum') ?? ''),
		email: String(formData.get('email') ?? ''),
		telefon: String(formData.get('telefon') ?? ''),
		adresse: {
			strasse: String(formData.get('strasse') ?? ''),
			plz: String(formData.get('plz') ?? ''),
			ort: String(formData.get('ort') ?? ''),
			land: String(formData.get('land') ?? '')
		},
		tarifId: String(formData.get('tarifId')) as TarifId,
		versicherungsbeginn: String(formData.get('versicherungsbeginn') ?? ''),
		vorversicherung: formData.get('vorversicherung') !== null,
		fehlendeZaehne: Number(formData.get('fehlendeZaehne') ?? 0),
		status: String(formData.get('status') ?? 'aktiv') as Kundenstatus
	};
}
