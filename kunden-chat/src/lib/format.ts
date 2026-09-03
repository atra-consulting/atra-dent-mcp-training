
import type { Geldbetrag } from './types/view';

const CURRENCY = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

const NUMBER = new Intl.NumberFormat('de-DE');

export function currency(amount: Geldbetrag | null | undefined): string | null {
	const number = asNumber(amount);
	return number === null ? null : CURRENCY.format(number);
}

export function number(value: number | null | undefined): string | null {
	return typeof value === 'number' && Number.isFinite(value) ? NUMBER.format(value) : null;
}

export function percent(value: number | null | undefined): string | null {
	const formatted = number(value);
	return formatted === null ? null : `${formatted} %`;
}

export function date(iso: string | null | undefined): string | null {
	if (!iso) return null;
	const value = new Date(iso);
	return Number.isNaN(value.getTime()) ? iso : value.toLocaleDateString('de-DE');
}

export function months(value: number | null | undefined): string | null {
	if (typeof value !== 'number' || !Number.isFinite(value)) return null;
	return value === 1 ? '1 Monat' : `${NUMBER.format(value)} Monate`;
}

export function asNumber(amount: Geldbetrag | null | undefined): number | null {
	if (typeof amount === 'number') return Number.isFinite(amount) ? amount : null;
	if (typeof amount !== 'string' || amount.trim() === '') return null;
	const value = Number.parseFloat(amount);
	return Number.isFinite(value) ? value : null;
}
