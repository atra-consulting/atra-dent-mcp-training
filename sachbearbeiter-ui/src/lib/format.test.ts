import { describe, expect, it } from 'vitest';
import {
	akteurLabel,
	ablehnungsgrund,
	dateTime,
	gozZustand,
	eskalationsgrund,
	schadensfallstatus,
	schadensfallstatusVariant
} from './format';

describe('schadensfallstatus', () => {
	it('knows all seven statuses', () => {
		expect(schadensfallstatus('geprueft_freigabe')).toBe('Geprüft – Freigabe empfohlen');
		expect(schadensfallstatus('geprueft_eskalation')).toBe('Geprüft – Eskalation');
		expect(schadensfallstatus('ausgezahlt')).toBe('Ausgezahlt');
		expect(schadensfallstatus('unbekannt')).toBe('unbekannt');
	});
});

describe('schadensfallstatusVariant', () => {
	it('maps every status onto a badge variant', () => {
		expect(schadensfallstatusVariant('abgelehnt')).toBe('destructive');
		expect(schadensfallstatusVariant('geprueft_freigabe')).toBe('default');
		expect(schadensfallstatusVariant('geprueft_eskalation')).toBe('secondary');
		expect(schadensfallstatusVariant('eingereicht')).toBe('outline');
		expect(schadensfallstatusVariant('irgendwas')).toBe('outline');
	});
});

describe('dateTime', () => {
	it('formats date and time in German and tolerates junk', () => {
		expect(dateTime('2026-08-18T10:05:00Z')).toMatch(/18\.08\.2026/);
		expect(dateTime(null)).toBe('—');
		expect(dateTime('kein datum')).toBe('—');
	});
});

describe('akteurLabel / eskalationsgrund', () => {
	it('translates the keys it knows and leaves the rest alone', () => {
		expect(akteurLabel('sachbearbeitung')).toBe('Sachbearbeitung');
		expect(akteurLabel('x')).toBe('x');
		expect(eskalationsgrund('GOZ_UNCLEAR')).toBe('GOZ-Zuordnung unklar');
		expect(eskalationsgrund('MODELL_ESCALATION')).toBe('Eskalation auf Einschätzung des Agenten');
		expect(eskalationsgrund('NEU')).toBe('NEU');
	});
});

describe('gozZustand', () => {
	it('translates all four states and leaves the rest alone', () => {
		expect(gozZustand('ENTHALTEN')).toBe('Versichert');
		expect(gozZustand('NICHT_ENTHALTEN')).toBe('Nicht versichert');
		expect(gozZustand('NICHT_BESTIMMBAR')).toBe('Nicht bestimmbar');
		expect(gozZustand('UNBEKANNT')).toBe('Unbekannt');
		expect(gozZustand('SONSTWAS')).toBe('SONSTWAS');
	});
});

describe('ablehnungsgrund', () => {
	it('translates all seven Ablehnungsgruende and leaves the rest alone', () => {
		expect(ablehnungsgrund('ANGERATEN')).toBe('Bereits angeraten/begonnen');
		expect(ablehnungsgrund('KOSMETIK')).toBe('Kosmetisch');
		expect(ablehnungsgrund('NICHT_APPROBIERT')).toBe('Behandler nicht approbiert');
		expect(ablehnungsgrund('FEHLENDE_ZAEHNE')).toBe('Fehlende Zähne bei Antrag');
		expect(ablehnungsgrund('NICHT_VERSICHERT')).toBe('Leistungsbereich nicht versichert');
		expect(ablehnungsgrund('WARTEZEIT')).toBe('Wartezeit läuft');
		expect(ablehnungsgrund('SONSTIGES')).toBe('Sonstiges');
		expect(ablehnungsgrund('UNBEKANNT')).toBe('UNBEKANNT');
	});
});
