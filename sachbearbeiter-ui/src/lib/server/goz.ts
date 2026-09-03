import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { env } from '$env/dynamic/private';
import { parse } from 'yaml';
import type { LeistungsbereichSchluessel } from '$lib/types/api';

export interface GozEntry {
	nummer: string;
	bezeichnung: string;
	abschnitt: string;
	leistungsbereich: LeistungsbereichSchluessel | null;
}

let loaded: GozEntry[] | null = null;

export function gozCatalog(): GozEntry[] {
	if (!loaded) {
		const path = resolve(process.cwd(), env.GOZ_YAML_PATH ?? '../wissen/daten/goz-zuordnung.yaml');
		const raw = parse(readFileSync(path, 'utf8')) as { positionen: GozEntry[] };
		loaded = raw.positionen.map((p) => ({
			nummer: p.nummer,
			bezeichnung: p.bezeichnung,
			abschnitt: p.abschnitt,
			leistungsbereich: p.leistungsbereich ?? null
		}));
	}
	return loaded;
}
