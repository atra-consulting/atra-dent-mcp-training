<script lang="ts">
	import { enhance } from '$app/forms';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Button } from '$lib/components/ui/button';
	import PageHeader from '$lib/components/page-header.svelte';
	import { euro } from '$lib/format';
	import type { TarifLeistung } from '$lib/types/tarifwerk';

	let { data, form } = $props();

	function leistungstext(leistung: TarifLeistung | undefined): string {
		if (!leistung?.versichert) return '—';
		let text = `${leistung.quote ?? 0} %`;
		if (leistung.limit_pro_jahr != null) text += `, max. ${euro(leistung.limit_pro_jahr)}/Jahr`;
		if (leistung.limit_gesamt != null) text += `, gesamt max. ${euro(leistung.limit_gesamt)}`;
		if (leistung.max_faelle != null)
			text += `, ${leistung.max_faelle} Fälle/${leistung.zeitraum_jahre} Jahre`;
		if (leistung.wartezeit_monate != null) text += `, Wartezeit ${leistung.wartezeit_monate} Mon.`;
		return text;
	}
</script>

<PageHeader
	variant="slim"
	eyebrow="Produktmodell"
	title="Tarifvergleich"
	subline="Leistungen, Grenzen und Beiträge der vier Tarife nebeneinander."
/>

<section class="mb-8 rounded-lg border p-4">
	<h2 class="mb-3 text-sm font-semibold">Beitragsrechner</h2>
	<form method="POST" action="?/berechnen" use:enhance class="flex flex-wrap items-end gap-4">
		<div class="grid gap-1.5">
			<Label for="geburtsdatum">Geburtsdatum</Label>
			<Input
				id="geburtsdatum"
				name="geburtsdatum"
				type="date"
				required
				value={form?.geburtsdatum ?? ''}
			/>
		</div>
		<div class="grid gap-1.5">
			<Label for="gewuenschterBeginn">Gewünschter Beginn</Label>
			<Input
				id="gewuenschterBeginn"
				name="gewuenschterBeginn"
				type="date"
				required
				value={form?.gewuenschterBeginn ?? ''}
			/>
		</div>
		<Button type="submit" class="bg-akzent-verlauf border-0 hover:opacity-90"
			>Beiträge berechnen</Button
		>
	</form>
	{#if form?.formError}<p class="mt-2 text-sm text-destructive">{form.formError}</p>{/if}
</section>

<div class="overflow-x-auto">
	<table class="w-full min-w-[56rem] border-collapse text-sm">
		<thead>
			<tr>
				<th class="border-b p-2 text-left"></th>
				{#each data.tarife as tarif (tarif.schluessel)}
					<th class="h-px border-b p-2 text-left align-top">
						<div class="flex h-full flex-col">
							<div class="font-semibold">{tarif.anzeigename}</div>
							<div class="text-xs font-normal text-muted-foreground">{tarif.positionierung}</div>
							{#if data.bedingungswerkPdfs[tarif.schluessel]}
								<a
									href="/wissen{data.bedingungswerkPdfs[tarif.schluessel]}"
									target="_blank"
									rel="noopener"
									class="mt-auto pt-1 text-xs font-normal text-primary hover:underline"
								>
									Bedingungswerk (PDF)
								</a>
							{/if}
						</div>
					</th>
				{/each}
			</tr>
		</thead>
		<tbody>
			<tr>
				<td class="border-b p-2 font-medium">Basisbeitrag ab 18–30 (Kernsystem)</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					<td class="border-b p-2"
						>{euro(
							data.tarifDtos.find((t) => t.id === tarif.schluessel)?.basisbeitragMonatlich
						)}</td
					>
				{/each}
			</tr>
			{#if form?.results}
				<tr class="bg-accent/50">
					<td class="border-b p-2 font-medium">Berechneter Beitrag</td>
					{#each data.tarife as tarif (tarif.schluessel)}
						{@const result = form.results[tarif.schluessel]}
						<td class="border-b p-2">
							{#if result?.monatsbeitrag}{euro(result.monatsbeitrag)}/Monat
							{:else}<span class="text-destructive">{result?.error}</span>{/if}
						</td>
					{/each}
				</tr>
			{/if}
			<tr>
				<td class="border-b p-2 font-medium">Selbstbehalt p. a.</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					<td class="border-b p-2"
						>{tarif.selbstbehalt === 0 ? 'keiner' : euro(tarif.selbstbehalt)}</td
					>
				{/each}
			</tr>
			<tr>
				<td class="border-b p-2 font-medium">Jahreshöchstgrenze</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					<td class="border-b p-2"
						>{tarif.jahreshoechstgrenze === null ? 'keine' : euro(tarif.jahreshoechstgrenze)}</td
					>
				{/each}
			</tr>
			<tr>
				<td class="border-b p-2 font-medium">Wartezeit</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					<td class="border-b p-2">
						{tarif.wartezeit_monate} Monate{tarif.wartezeit_entfaellt_bei_vorversicherung
							? ' (entfällt bei Vorversicherung)'
							: ''}
					</td>
				{/each}
			</tr>
			<tr>
				<td class="border-b p-2 font-medium">Eintrittsalter</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					<td class="border-b p-2">{tarif.eintrittsalter.von}–{tarif.eintrittsalter.bis} Jahre</td>
				{/each}
			</tr>
			{#each data.leistungsbereiche as leistungsbereich (leistungsbereich.schluessel)}
				<tr>
					<td class="border-b p-2">
						<span class="font-medium">{leistungsbereich.schluessel}</span>
						<span class="text-muted-foreground"> {leistungsbereich.name}</span>
					</td>
					{#each data.tarife as tarif (tarif.schluessel)}
						<td class="border-b p-2"
							>{leistungstext(tarif.leistungen?.[leistungsbereich.schluessel])}</td
						>
					{/each}
				</tr>
			{/each}
			<tr>
				<td class="border-b p-2 font-medium">Zahnstaffel</td>
				{#each data.tarife as tarif (tarif.schluessel)}
					{@const staffel = data.staffeln.find((s) => s.schluessel === tarif.staffel)}
					<td class="border-b p-2">
						{#if staffel}
							{#each staffel.stufen as step, i (i)}
								{#if step.bis_jahr !== null}
									<div>
										bis Jahr {step.bis_jahr}: {step.betrag === null
											? 'unbegrenzt'
											: euro(step.betrag)}
									</div>
								{:else}
									<div>danach: unbegrenzt</div>
								{/if}
							{/each}
						{/if}
					</td>
				{/each}
			</tr>
		</tbody>
	</table>
</div>

<p class="mt-4 text-xs text-muted-foreground">
	Leistungsdetails aus wissen/daten/tarife.yaml; Beiträge aus dem Kernsystem. Die Zahnstaffel
	entfällt bei unfallbedingten Behandlungen.
</p>
