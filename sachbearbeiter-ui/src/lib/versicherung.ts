function asLocalDate(iso: string): Date {
	const [year, month, day] = iso.split('-').map(Number);
	return new Date(year, month - 1, day);
}

export function currentVersicherungsjahr(
	versicherungsbeginn: string,
	today: Date = new Date()
): number {
	const beginDate = asLocalDate(versicherungsbeginn);
	let fullYears = today.getFullYear() - beginDate.getFullYear();
	const anniversary = new Date(beginDate);
	anniversary.setFullYear(beginDate.getFullYear() + fullYears);
	if (today < anniversary) fullYears -= 1;
	return Math.max(1, fullYears + 1);
}

export function beitragsmonateSince(versicherungsbeginn: string, today: Date = new Date()): number {
	const beginDate = asLocalDate(versicherungsbeginn);
	if (today < beginDate) return 0;
	let months =
		(today.getFullYear() - beginDate.getFullYear()) * 12 +
		(today.getMonth() - beginDate.getMonth());
	if (today.getDate() < beginDate.getDate()) months -= 1;
	return Math.max(1, months + 1);
}

export function inVersicherungsjahr(
	iso: string,
	versicherungsbeginn: string,
	year: number
): boolean {
	const beginDate = asLocalDate(versicherungsbeginn);
	const from = new Date(beginDate);
	from.setFullYear(beginDate.getFullYear() + year - 1);
	const to = new Date(beginDate);
	to.setFullYear(beginDate.getFullYear() + year);
	const date = asLocalDate(iso);
	return date >= from && date < to;
}
