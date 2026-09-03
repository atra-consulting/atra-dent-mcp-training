import { describe, expect, it } from 'vitest';
import { asNumber, date, currency, months, percent } from './format';

const NBSP = ' ';

describe('currency', () => {
	it('shows both forms the same way', () => {
		expect(currency('450.00')).toBe(currency(450));
		expect(currency('450.00')).toBe(`450,00${NBSP}€`);
	});

	it('takes a decimal string without two decimal places too', () => {
		expect(currency('12.3')).toBe(`12,30${NBSP}€`);
	});

	it('does not turn missing into zero', () => {
		expect(currency(null)).toBeNull();
		expect(currency(undefined)).toBeNull();
		expect(currency('')).toBeNull();
	});

	it('does not turn something unreadable into a number', () => {
		expect(currency('keine Zahl')).toBeNull();
	});
});

describe('asNumber', () => {
	it('reads both forms', () => {
		expect(asNumber('24.90')).toBe(24.9);
		expect(asNumber(24.9)).toBe(24.9);
	});

	it('returns null for something unreadable, not 0', () => {
		expect(asNumber('—')).toBeNull();
		expect(asNumber(null)).toBeNull();
		expect(asNumber(Number.NaN)).toBeNull();
	});
});

describe('percent and months', () => {
	it('labels Quoten and Wartezeiten', () => {
		expect(percent(80)).toBe('80 %');
		expect(months(8)).toBe('8 Monate');
		expect(months(1)).toBe('1 Monat');
	});

	it('lets missing values stay missing', () => {
		expect(percent(null)).toBeNull();
		expect(months(undefined)).toBeNull();
	});
});

describe('date', () => {
	it('writes an ISO date the German way', () => {
		expect(date('2024-03-01')).toBe('1.3.2024');
	});

	it('passes something unreadable through unchanged', () => {
		expect(date('demnaechst')).toBe('demnaechst');
		expect(date(null)).toBeNull();
	});
});
