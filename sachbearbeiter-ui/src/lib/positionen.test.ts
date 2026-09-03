import { describe, expect, it } from 'vitest';
import { findingForPosition, orphanedFindings } from './positionen';
import type { Bewertung, Bewertungsposition, Schadenposition } from './types/api';

function position(parts: Partial<Schadenposition> = {}): Schadenposition {
	return {
		goz: null,
		leistungsbereich: null,
		betrag: '100.00',
		beschreibung: 'Position',
		...parts
	};
}

function finding(parts: Partial<Bewertungsposition> = {}): Bewertungsposition {
	return {
		index: null,
		goz: null,
		leistungsbereich: null,
		zustand: 'NICHT_BESTIMMBAR',
		begruendung: 'Begruendung',
		...parts
	};
}

function bewertung(positionen: Bewertungsposition[]): Bewertung {
	return {
		empfehlung: 'freigabe',
		erstattungsvorschlag: '100.00',
		eskalationsgruende: [],
		positionen,
		arztauskunft: null,
		begruendung: 'Begruendung',
		agent: 'atra.dent Schadensfallagent',
		modell: null,
		zeitpunkt: '2026-08-19T10:00:00Z'
	};
}

describe('findingForPosition', () => {
	it('gives every Position without a Gebuehrennummer its own finding', () => {
		const material = position({ beschreibung: 'Implantatkoerper', zahn: '46' });
		const labor = position({ beschreibung: 'Laborrechnung Krone', zahn: '46' });
		const assessment = bewertung([
			finding({ index: 0, leistungsbereich: 'IMP', zustand: 'ENTHALTEN', begruendung: 'Material' }),
			finding({ index: 1, leistungsbereich: 'ZE', zustand: 'ENTHALTEN', begruendung: 'Labor' })
		]);

		expect(findingForPosition(assessment, material, 0)?.begruendung).toBe('Material');
		expect(findingForPosition(assessment, material, 0)?.leistungsbereich).toBe('IMP');
		expect(findingForPosition(assessment, labor, 1)?.begruendung).toBe('Labor');
		expect(findingForPosition(assessment, labor, 1)?.leistungsbereich).toBe('ZE');
	});

	it('tells two identical Gebuehrennummern apart', () => {
		const first = position({ goz: '9010', zahn: '36', datum: '2026-06-08' });
		const second = position({ goz: '9010', zahn: '36', datum: '2026-07-08' });
		const assessment = bewertung([
			finding({ index: 0, goz: '9010', begruendung: 'Erste Insertion' }),
			finding({ index: 1, goz: '9010', begruendung: 'Zweite Insertion' })
		]);

		expect(findingForPosition(assessment, first, 0)?.begruendung).toBe('Erste Insertion');
		expect(findingForPosition(assessment, second, 1)?.begruendung).toBe('Zweite Insertion');
	});

	it('finds a legacy finding through its Gebuehrennummer', () => {
		const behandlung = position({ goz: '2040' });
		const assessment = bewertung([finding({ goz: '2040', begruendung: 'Fuellung' })]);

		expect(findingForPosition(assessment, behandlung, 0)?.begruendung).toBe('Fuellung');
	});

	it('does not guess where neither side carries a Gebuehrennummer', () => {
		const material = position();
		const assessment = bewertung([finding({ begruendung: 'irgendetwas' })]);

		expect(findingForPosition(assessment, material, 0)).toBeUndefined();
	});

	it('returns nothing where there is no Bewertung', () => {
		expect(findingForPosition(null, position(), 0)).toBeUndefined();
		expect(findingForPosition(undefined, position(), 0)).toBeUndefined();
	});

	it('an indexed finding does not reach a foreign Position through its Gebuehrennummer', () => {
		const first = position({ goz: '9010', zahn: '36', datum: '2026-06-08' });
		const second = position({ goz: '9010', zahn: '36', datum: '2026-07-08' });
		const assessment = bewertung([
			finding({ index: 0, goz: '9010', begruendung: 'gehoert zu Position 0' }),
			finding({ goz: '2080', begruendung: 'Altbestand, andere Nummer' })
		]);

		expect(findingForPosition(assessment, first, 0)?.begruendung).toBe('gehoert zu Position 0');
		expect(findingForPosition(assessment, second, 1)).toBeUndefined();
	});

	it('returns nothing for a Position no finding names', () => {
		const assessment = bewertung([finding({ index: 0, goz: '2040' })]);

		expect(findingForPosition(assessment, position({ goz: '9010' }), 1)).toBeUndefined();
	});
});

describe('orphanedFindings', () => {
	it('reports a finding whose index the Schadensfall does not have', () => {
		const positionen = [position({ goz: '2080' }), position({ goz: '2040' })];
		const assessment = bewertung([
			finding({ index: 0, goz: '2080', begruendung: 'zur ersten Position' }),
			finding({ index: 7, goz: '9010', begruendung: 'zu einer Position, die es nicht gibt' })
		]);

		expect(orphanedFindings(assessment, positionen).map((b) => b.begruendung)).toEqual([
			'zu einer Position, die es nicht gibt'
		]);
	});

	it('reports the second finding on the same index', () => {
		const positionen = [position({ goz: '2080' })];
		const assessment = bewertung([
			finding({ index: 0, begruendung: 'erster' }),
			finding({ index: 0, begruendung: 'zweiter, wird nirgends gezeigt' })
		]);

		expect(orphanedFindings(assessment, positionen).map((b) => b.begruendung)).toEqual([
			'zweiter, wird nirgends gezeigt'
		]);
	});

	it('reports nothing where every finding finds its line', () => {
		const positionen = [position({ goz: '2080' }), position()];
		const assessment = bewertung([finding({ index: 0 }), finding({ index: 1 })]);

		expect(orphanedFindings(assessment, positionen)).toEqual([]);
	});

	it('reports nothing where there is no Bewertung', () => {
		expect(orphanedFindings(null, [position()])).toEqual([]);
	});

	it('a legacy finding is not orphaned', () => {
		const positionen = [position({ goz: '2040' })];
		const assessment = bewertung([finding({ goz: '2040' })]);

		expect(orphanedFindings(assessment, positionen)).toEqual([]);
	});
});
