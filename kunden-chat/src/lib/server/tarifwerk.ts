import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parse } from 'yaml';
import { env } from '$env/dynamic/private';
import type { Tarifwerk } from '$lib/types/tarifwerk';

let loaded: Tarifwerk | null = null;

export function tarifwerk(): Tarifwerk {
	if (!loaded) {
		const path = resolve(process.cwd(), env.TARIFE_YAML_PATH ?? '../wissen/daten/tarife.yaml');
		const catalog = parse(readFileSync(path, 'utf8')) as Tarifwerk;
		for (const tariff of catalog.tarife) {
			if (!tariff.leistungen && tariff.leistungen_wie) {
				tariff.leistungen = catalog.tarife.find(
					(t) => t.schluessel === tariff.leistungen_wie
				)?.leistungen;
			}
		}
		loaded = catalog;
	}
	return loaded;
}

export function staffelbetrag(catalog: Tarifwerk, tarifId: string, year: number): number | null {
	const tariff = catalog.tarife.find((t) => t.schluessel === tarifId);
	const tier = catalog.staffeln.find((s) => s.schluessel === tariff?.staffel);
	if (!tier) return null;
	for (const step of tier.stufen) {
		if (step.bis_jahr === null || year <= step.bis_jahr) return step.betrag;
	}
	return null;
}
