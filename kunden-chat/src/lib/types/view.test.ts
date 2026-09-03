import { describe, expect, it } from 'vitest';
import {
	ablehnungsgrundLabel,
	MAX_VIEWS,
	isView,
	STAGE_LABELS,
	statusNote,
	statusStage,
	viewsFrom
} from './view';

describe('isView', () => {
	it('recognizes a view by its kind', () => {
		expect(isView({ kind: 'tarifvergleich' })).toBe(true);
		expect(isView({ kind: 'schadensfaelle', faelle: [] })).toBe(true);
	});

	it('lets hymne through', () => {
		expect(isView({ kind: 'hymne' })).toBe(true);
	});

	it('recognizes the read-back of the Kontaktdaten', () => {
		expect(
			isView({ kind: 'kontaktdaten', adresse: { strasse: 'Lindenallee 7', ort: 'Hamburg' } })
		).toBe(true);
	});

	it('passes over an unknown kind silently', () => {
		expect(isView({ kind: 'erstattungsverlauf' })).toBe(false);
	});

	it('is not fooled by anything', () => {
		expect(isView(null)).toBe(false);
		expect(isView('tarifvergleich')).toBe(false);
		expect(isView({})).toBe(false);
	});
});

describe('viewsFrom', () => {
	it('takes at most two', () => {
		const viele = [
			{ kind: 'tarifvergleich' },
			{ kind: 'beitragsvergleich' },
			{ kind: 'tarifempfehlung' }
		];

		expect(viewsFrom(viele)).toHaveLength(MAX_VIEWS);
		expect(viewsFrom(viele).map((view) => view.kind)).toEqual([
			'tarifvergleich',
			'beitragsvergleich'
		]);
	});

	it('sorts out what is unknown without losing the rest', () => {
		const gemischt = [{ kind: 'erstattungsverlauf' }, { kind: 'vertrag' }];

		expect(viewsFrom(gemischt).map((view) => view.kind)).toEqual(['vertrag']);
	});

	it('is content with a missing field', () => {
		expect(viewsFrom(undefined)).toEqual([]);
		expect(viewsFrom({})).toEqual([]);
		expect(viewsFrom([])).toEqual([]);
	});
});

describe('statusStage', () => {
	it('maps the seven statuses onto five stages', () => {
		expect(statusStage('eingereicht')).toMatchObject({ stage: 0, label: 'Eingereicht' });
		expect(statusStage('in_pruefung')).toMatchObject({ stage: 1 });
		expect(statusStage('geprueft_freigabe')).toMatchObject({ stage: 2, label: 'Geprüft' });
		expect(statusStage('geprueft_eskalation')).toMatchObject({ stage: 2, atSachbearbeitung: true });
		expect(statusStage('genehmigt')).toMatchObject({ stage: 3 });
		expect(statusStage('ausgezahlt')).toMatchObject({ stage: 4 });
		expect(statusStage('abgelehnt')).toMatchObject({ rejected: true, label: 'Abgelehnt' });
	});
	it('handles the unknown without guessing', () => {
		expect(statusStage(undefined)).toMatchObject({ stage: 0, label: '—' });
		expect(statusStage('irgendwas')).toMatchObject({ stage: 0, label: 'irgendwas' });
	});
	it('is not fooled by an inherited object property', () => {
		expect(statusStage('toString')).toMatchObject({ stage: 0, label: 'toString' });
	});
});

describe('ablehnungsgrundLabel', () => {
	it('turns every known Ablehnungsgrund into a sentence for a Kundin', () => {
		expect(ablehnungsgrundLabel('ANGERATEN')).toBe('Behandlung bereits angeraten/begonnen');
		expect(ablehnungsgrundLabel('KOSMETIK')).toBe('Kosmetische Leistung');
		expect(ablehnungsgrundLabel('NICHT_APPROBIERT')).toBe('Behandler nicht approbiert');
		expect(ablehnungsgrundLabel('FEHLENDE_ZAEHNE')).toBe('Fehlende Zähne bei Antragstellung');
		expect(ablehnungsgrundLabel('NICHT_VERSICHERT')).toBe('Leistungsbereich nicht versichert');
		expect(ablehnungsgrundLabel('WARTEZEIT')).toBe('Wartezeit noch nicht abgelaufen');
		expect(ablehnungsgrundLabel('SONSTIGES')).toBe('Sonstiger Grund');
	});

	it('guesses nothing for an unknown or missing Ablehnungsgrund', () => {
		expect(ablehnungsgrundLabel('irgendwas')).toBe('Abgelehnt');
		expect(ablehnungsgrundLabel(null)).toBe('Abgelehnt');
		expect(ablehnungsgrundLabel(undefined)).toBe('Abgelehnt');
	});

	it('is fooled neither by an inherited property nor by an empty string', () => {
		expect(ablehnungsgrundLabel('toString')).toBe('Abgelehnt');
		expect(ablehnungsgrundLabel('')).toBe('Abgelehnt');
	});
});

describe('statusNote', () => {
	it('says nothing where the stage of the Bearbeitungsstand already says it', () => {
		expect(statusNote('eingereicht')).toBeNull();
		expect(statusNote('geprueft_freigabe')).toBeNull();
		expect(statusNote('genehmigt')).toBeNull();
		expect(statusNote('ausgezahlt')).toBeNull();
	});

	it('names the state that no stage of the Bearbeitungsstand carries', () => {
		expect(statusNote('geprueft_eskalation')).toBe('Liegt bei der Sachbearbeitung');
		expect(statusNote('abgelehnt')).toBe('Abgelehnt');
	});

	it('names a status it does not know instead of swallowing it', () => {
		expect(statusNote('irgendwas')).toBe('irgendwas');
	});

	it('says nothing where there is no status', () => {
		expect(statusNote(undefined)).toBeNull();
	});
});

describe('STAGE_LABELS', () => {
	it('carries one label per stage of the Bearbeitungsstand', () => {
		expect(STAGE_LABELS).toEqual([
			'Eingereicht',
			'In Prüfung',
			'Geprüft',
			'Genehmigt',
			'Ausgezahlt'
		]);
	});
});
