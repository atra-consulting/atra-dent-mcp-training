import type { Kunde } from '$lib/types/api';

const SEED_AVATARS: Record<number, string> = {
	10001: 'w-44.jpg',
	10002: 'm-75.jpg',
	10003: 'w-68.jpg',
	10004: 'm-32.jpg',
	10005: 'w-21.jpg',
	10006: 'm-85.jpg',
	10007: 'm-3.jpg',
	10009: 'm-5.jpg',
	10013: 'm-18.jpg',
	10023: 'm-37.jpg',
	10026: 'w-47.jpg',
	10034: 'w-62.jpg',
	10035: 'm-62.jpg',
	10042: 'w-82.jpg',
	10048: 'w-92.jpg'
};

const POOL = [
	'm-11.jpg',
	'm-23.jpg',
	'm-52.jpg',
	'm-60.jpg',
	'm-8.jpg',
	'm-93.jpg',
	'm-97.jpg',
	'w-33.jpg',
	'w-65.jpg',
	'w-90.jpg'
];

export function avatarUrl(customer: Pick<Kunde, 'id'>): string {
	const file = SEED_AVATARS[customer.id] ?? POOL[customer.id % POOL.length];
	return `/avatare/${file}`;
}
