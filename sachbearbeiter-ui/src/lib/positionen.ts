import type { Bewertung, Bewertungsposition, Schadenposition } from '$lib/types/api';

export function findingForPosition(
	bewertung: Bewertung | null | undefined,
	position: Schadenposition,
	index: number
): Bewertungsposition | undefined {
	const findings = bewertung?.positionen;
	if (!findings) return undefined;
	const byIndex = findings.find((finding) => finding.index != null && finding.index === index);
	if (byIndex) return byIndex;
	if (position.goz == null) return undefined;
	return findings.find((finding) => finding.index == null && finding.goz === position.goz);
}

export function orphanedFindings(
	bewertung: Bewertung | null | undefined,
	positionen: Schadenposition[]
): Bewertungsposition[] {
	const findings = bewertung?.positionen;
	if (!findings) return [];
	const shown = new Set(
		positionen.map((position, index) => findingForPosition(bewertung, position, index))
	);
	return findings.filter((finding) => !shown.has(finding));
}
