import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parse } from 'yaml';
import { env } from '$env/dynamic/private';
import type { Tarifwerk } from '$lib/types/tarifwerk';

let loaded: Tarifwerk | null = null;

export function tarifwerk(): Tarifwerk {
	if (!loaded) {
		const path = resolve(process.cwd(), env.TARIFE_YAML_PATH ?? '../wissen/daten/tarife.yaml');
		const parsed = parse(readFileSync(path, 'utf8')) as Tarifwerk;
		for (const tarif of parsed.tarife) {
			if (!tarif.leistungen && tarif.leistungen_wie) {
				tarif.leistungen = parsed.tarife.find(
					(t) => t.schluessel === tarif.leistungen_wie
				)?.leistungen;
			}
		}
		loaded = parsed;
	}
	return loaded;
}

export function staffelbetrag(tarifwerk: Tarifwerk, tarifId: string, year: number): number | null {
	const tarif = tarifwerk.tarife.find((t) => t.schluessel === tarifId);
	const staffel = tarifwerk.staffeln.find((s) => s.schluessel === tarif?.staffel);
	if (!staffel) return null;
	for (const step of staffel.stufen) {
		if (step.bis_jahr === null || year <= step.bis_jahr) return step.betrag;
	}
	return null;
}
