<script lang="ts">
	import { enhance } from '$app/forms';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Button } from '$lib/components/ui/button';
	import * as Alert from '$lib/components/ui/alert';
	import PageHeader from '$lib/components/page-header.svelte';
	import { euro, geldbetragFromInput } from '$lib/format';

	import type { Gozstatus } from '$lib/types/wissen';

	let { data, form } = $props();

	type Row = { goz: string; leistungsbereich: string; betrag: string; beschreibung: string };
	let rows = $state<Row[]>([{ goz: '', leistungsbereich: 'ZE', betrag: '', beschreibung: '' }]);

	const sum = $derived(
		rows.reduce((s, r) => s + Number.parseFloat(geldbetragFromInput(r.betrag) ?? '0'), 0)
	);
	const positionsJson = $derived(JSON.stringify(rows));

	const findings = $derived(form && 'findings' in form ? form.findings : null);

	const gozByNummer = $derived(new Map(data.gozEntries.map((entry) => [entry.nummer, entry])));

	function applyGozEntry(row: Row) {
		const entry = gozByNummer.get(row.goz);
		if (!entry) return;
		if (entry.leistungsbereich) row.leistungsbereich = entry.leistungsbereich;
		if (!row.beschreibung.trim()) row.beschreibung = entry.bezeichnung;
	}

	const statusStyles: Record<Gozstatus, string> = {
		ENTHALTEN: 'border-primary/30 bg-primary/10 text-primary',
		NICHT_ENTHALTEN: 'border-destructive/40 bg-destructive/10 text-destructive',
		NICHT_BESTIMMBAR: 'border-akzent-hell/50 bg-akzent-hell/15 text-akzent',
		UNBEKANNT: 'border-akzent-hell/50 bg-akzent-hell/15 text-akzent'
	};
</script>

<PageHeader
	variant="slim"
	eyebrow="Akte {data.kunde.id}"
	eyebrowHref="/kunden/{data.kunde.id}"
	title="Schadensfall erfassen"
	subline="Für {data.kunde.nachname}, {data.kunde.vorname}"
/>

{#if form?.error}
	<Alert.Root variant="destructive" class="mb-4">
		<Alert.Title>Einreichen fehlgeschlagen</Alert.Title>
		<Alert.Description>{form.error}</Alert.Description>
	</Alert.Root>
{/if}

<form
	method="POST"
	action="?/einreichen"
	use:enhance={() => {
		return async ({ update }) => {
			await update({ reset: false });
		};
	}}
	class="space-y-4"
>
	<div class="grid max-w-xs gap-1.5">
		<Label for="behandlungsdatum">Behandlungsdatum</Label>
		<Input id="behandlungsdatum" name="behandlungsdatum" type="date" required />
	</div>

	<h2 class="text-sm font-semibold">Positionen</h2>
	{#each rows as row, i (i)}
		<div
			class="grid grid-cols-2 items-end gap-2 lg:grid-cols-[6rem_minmax(0,12rem)_minmax(0,1fr)_7rem_auto]"
		>
			<div class="grid min-w-0 gap-1">
				<Label for="goz-{i}">GOZ</Label>
				<Input
					id="goz-{i}"
					placeholder="9010 oder leer"
					list="goz-katalog"
					bind:value={row.goz}
					oninput={() => applyGozEntry(row)}
				/>
			</div>
			<div class="grid min-w-0 gap-1">
				<Label for="bereich-{i}">Bereich</Label>
				<select
					id="bereich-{i}"
					bind:value={row.leistungsbereich}
					class="h-9 w-full min-w-0 rounded-md border border-input bg-background px-2 text-sm"
				>
					{#each data.leistungsbereiche as bereich (bereich.schluessel)}
						<option value={bereich.schluessel}>{bereich.schluessel} — {bereich.name}</option>
					{/each}
				</select>
			</div>
			<div class="col-span-2 grid min-w-0 gap-1 lg:col-span-1">
				<Label for="text-{i}">Beschreibung</Label>
				<Input
					id="text-{i}"
					placeholder="Leistungstext laut Rechnung"
					bind:value={row.beschreibung}
				/>
			</div>
			<div class="grid min-w-0 gap-1">
				<Label for="betrag-{i}">Betrag</Label>
				<Input id="betrag-{i}" placeholder="450 oder 450,50" bind:value={row.betrag} />
			</div>
			<Button
				type="button"
				variant="outline"
				size="sm"
				disabled={rows.length === 1}
				onclick={() => (rows = rows.filter((_, index) => index !== i))}>Entfernen</Button
			>
		</div>
		{#if gozByNummer.get(row.goz)}
			{@const entry = gozByNummer.get(row.goz)}
			<p class="-mt-2 pl-1 text-xs text-muted-foreground">
				GOZ {row.goz} · Abschnitt {entry?.abschnitt}: {entry?.bezeichnung}
				{#if entry?.leistungsbereich === null}
					— keinem Leistungsbereich zuordenbar (gehört zur Hauptbehandlung)
				{/if}
			</p>
		{/if}
		{#if findings?.[i]}
			{@const finding = findings[i]}
			<div class="-mt-2 flex flex-wrap items-center gap-2 pl-1 text-xs">
				<span class="rounded-full border px-2 py-0.5 font-medium {statusStyles[finding.status]}">
					{finding.status}{finding.quote != null ? ` · ${finding.quote} %` : ''}
				</span>
				{#if finding.bezeichnung}
					<span class="font-medium">{finding.bezeichnung}</span>
				{/if}
				<span class="text-muted-foreground">{finding.begruendung}</span>
			</div>
		{/if}
	{/each}

	<div class="flex items-center justify-between">
		<Button
			type="button"
			variant="outline"
			size="sm"
			onclick={() =>
				(rows = [...rows, { goz: '', leistungsbereich: 'ZE', betrag: '', beschreibung: '' }])}
		>
			Position hinzufügen
		</Button>
		<p class="text-sm">
			Rechnungssumme: <span class="font-medium">{euro(sum)}</span>
			<span class="text-muted-foreground">(ermittelt das Kernsystem — wird nicht gesendet)</span>
		</p>
	</div>

	<input type="hidden" name="positionen" value={positionsJson} />

	<datalist id="goz-katalog">
		{#each data.gozEntries as entry (entry.nummer)}
			<option value={entry.nummer}>{entry.bezeichnung}</option>
		{/each}
	</datalist>

	<div class="flex flex-wrap gap-3">
		<Button type="submit">Einreichen</Button>
		<Button type="submit" formaction="?/gozPruefen" formnovalidate variant="secondary">
			Positionen gegen Tarif prüfen
		</Button>
		<Button type="button" variant="outline" href="/kunden/{data.kunde.id}">Abbrechen</Button>
	</div>
	{#if findings}
		<p class="text-xs text-muted-foreground">
			Prüfung gegen {data.kunde.tarifId} (Wissensdienst). NICHT_BESTIMMBAR und UNBEKANNT sind keine Ablehnung,
			sondern eine Rückfrage an die Behandlungspraxis.
		</p>
	{/if}
</form>
