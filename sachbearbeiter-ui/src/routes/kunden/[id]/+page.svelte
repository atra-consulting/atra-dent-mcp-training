<script lang="ts">
	import TrendingDown from '@lucide/svelte/icons/trending-down';
	import TrendingUp from '@lucide/svelte/icons/trending-up';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { date, euro } from '$lib/format';
	import PageHeader from '$lib/components/page-header.svelte';
	import SchadensfallTable from '$lib/components/schadensfall-table.svelte';
	import SchadensfallEditDialog from '$lib/components/schadensfall-edit-dialog.svelte';
	import { invalidateAll } from '$app/navigation';
	import type { Schadensfall } from '$lib/types/api';

	let { data, form } = $props();
	const kunde = $derived(data.kunde);
	let editing = $state<Schadensfall | null>(null);

	$effect(() => {
		if (form && 'edited' in form) {
			editing = null;
			invalidateAll();
		}
	});
</script>

{#snippet fact(label: string, value: string)}
	<div class="min-w-0">
		<p class="text-xs tracking-wide text-muted-foreground uppercase">{label}</p>
		<p class="mt-1 text-sm font-medium wrap-anywhere">{value}</p>
	</div>
{/snippet}

{#snippet stat(label: string, value: string)}
	<div class="min-w-0">
		<p class="text-xs tracking-wide text-muted-foreground uppercase">{label}</p>
		<p class="mt-1 text-xl font-semibold wrap-anywhere text-primary">{value}</p>
	</div>
{/snippet}

<PageHeader
	eyebrow="Bestand"
	eyebrowHref="/kunden"
	title="{kunde.vorname} {kunde.nachname}"
	subline="Kundennummer {kunde.id}"
>
	{#snippet media()}
		<img src={data.avatar} alt="" class="size-14 shrink-0 rounded-full ring-2 ring-white/25" />
	{/snippet}
	{#snippet actions()}
		<Button variant="secondary" href="/kunden/{kunde.id}/bearbeiten">Bearbeiten</Button>
	{/snippet}

	<div class="mt-5 flex flex-wrap items-center gap-2">
		<Badge variant="secondary">{data.tarifName}</Badge>
		{#if kunde.status === 'inaktiv'}
			<Badge class="border-akzent-hell/50 bg-akzent/25 text-akzent-hell">stillgelegt</Badge>
		{:else}
			<Badge class="border-white/25 bg-white/10 text-white">aktiv</Badge>
		{/if}
		{#if data.balance !== null}
			<Badge
				class={data.balance >= 0
					? 'border-white/25 bg-white/10 text-white'
					: 'border-transparent bg-destructive text-destructive-foreground'}
			>
				{#if data.balance >= 0}
					<TrendingUp class="size-3.5" aria-hidden="true" />
					profitabel · +{euro(data.balance)}
				{:else}
					<TrendingDown class="size-3.5" aria-hidden="true" />
					defizitär · {euro(data.balance)}
				{/if}
			</Badge>
		{/if}
	</div>
</PageHeader>

<Card.Root
	class="mb-6 border-l-4 {data.balance !== null && data.balance < 0
		? 'border-l-destructive'
		: 'border-l-primary'}"
>
	<Card.Header class="pb-2">
		<Card.Title class="text-base">Wirtschaftlichkeit</Card.Title>
		<Card.Description>
			Geschätzt: heutiger Monatsbeitrag mal {data.beitragsmonate} Beitragsmonate seit Versicherungsbeginn
			— keine Werte des Kernsystems.
		</Card.Description>
	</Card.Header>
	<Card.Content class="grid grid-cols-2 gap-4 lg:grid-cols-4">
		{@render stat(
			'Beiträge seit Beginn',
			data.beitraegeTotal === null ? '—' : euro(data.beitraegeTotal)
		)}
		{@render stat('Erstattet gesamt', euro(data.erstattungTotal))}
		<div class="min-w-0">
			<p class="text-xs tracking-wide text-muted-foreground uppercase">Saldo</p>
			<p
				class="mt-1 text-xl font-semibold wrap-anywhere {data.balance !== null && data.balance < 0
					? 'text-destructive'
					: 'text-primary'}"
			>
				{#if data.balance === null}—{:else}{data.balance > 0 ? '+' : ''}{euro(data.balance)}{/if}
			</p>
		</div>
		<div class="min-w-0">
			<p class="text-xs tracking-wide text-muted-foreground uppercase">Schadenquote</p>
			<p
				class="mt-1 text-xl font-semibold wrap-anywhere {data.lossRatio !== null &&
				data.lossRatio > 1
					? 'text-destructive'
					: 'text-primary'}"
			>
				{data.lossRatio === null ? '—' : `${Math.round(data.lossRatio * 100)} %`}
			</p>
			{#if data.openSchadensfaelleCount > 0}
				<p class="mt-1 text-xs text-muted-foreground">
					{data.openSchadensfaelleCount} offene Fälle über {euro(data.openSchadensfaelleTotal)}
				</p>
			{/if}
		</div>
	</Card.Content>
</Card.Root>

<section>
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Stammdaten
	</h2>
	<div class="grid gap-4 lg:grid-cols-2">
		<Card.Root>
			<Card.Header>
				<Card.Title class="text-base">Person</Card.Title>
			</Card.Header>
			<Card.Content class="grid grid-cols-2 gap-4">
				{@render fact('Vorname', kunde.vorname)}
				{@render fact('Nachname', kunde.nachname)}
				{@render fact('Geburtsdatum', date(kunde.geburtsdatum))}
				{@render fact('Status', kunde.status ?? 'aktiv')}
			</Card.Content>
		</Card.Root>
		<Card.Root>
			<Card.Header>
				<Card.Title class="text-base">Kontakt &amp; Adresse</Card.Title>
			</Card.Header>
			<Card.Content class="grid grid-cols-2 gap-4">
				{@render fact('E-Mail', kunde.email)}
				{@render fact('Telefon', kunde.telefon)}
				<div class="col-span-2">
					{@render fact(
						'Adresse',
						`${kunde.adresse.strasse}, ${kunde.adresse.plz} ${kunde.adresse.ort}, ${kunde.adresse.land}`
					)}
				</div>
			</Card.Content>
		</Card.Root>
	</div>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Vertrag
	</h2>
	<div class="grid gap-4 lg:grid-cols-[2fr_3fr]">
		<Card.Root class="border-l-4 border-l-akzent">
			<Card.Header>
				<Card.Description>Tarif</Card.Description>
				<Card.Title class="text-xl">{data.tarifName}</Card.Title>
			</Card.Header>
			<Card.Content class="space-y-3">
				{#if data.tarifPositioning}
					<p class="text-sm text-muted-foreground">{data.tarifPositioning}</p>
				{/if}
				{#if data.monatsbeitrag}
					<p class="text-3xl font-bold text-primary">
						{euro(data.monatsbeitrag)}
						<span class="text-sm font-normal text-muted-foreground">/ Monat</span>
					</p>
				{/if}
				<p class="text-xs text-muted-foreground">{kunde.tarifId}</p>
				{#if data.bedingungswerkDocument}
					<Button
						variant="outline"
						size="sm"
						href="/wissen{data.bedingungswerkDocument.pdfUrl}"
						target="_blank"
						rel="noopener"
					>
						Bedingungswerk (PDF)
					</Button>
				{/if}
			</Card.Content>
		</Card.Root>
		<Card.Root>
			<Card.Header>
				<Card.Title class="text-base">Vertragsdaten</Card.Title>
			</Card.Header>
			<Card.Content class="grid grid-cols-2 gap-4 sm:grid-cols-3">
				{@render fact('Versicherungsbeginn', date(kunde.versicherungsbeginn))}
				{@render fact('Versicherungsjahr', `Jahr ${data.versicherungsjahr}`)}
				{@render fact('Vorversicherung', kunde.vorversicherung ? 'ja' : 'nein')}
				{@render fact('Fehlende Zähne', String(kunde.fehlendeZaehne))}
				{@render fact(
					'Selbstbehalt p. a.',
					data.selbstbehalt === 0 ? 'keiner' : euro(data.selbstbehalt)
				)}
				{@render fact(
					'Jahreshöchstgrenze',
					data.jahreshoechstgrenze === null ? 'keine' : euro(data.jahreshoechstgrenze)
				)}
			</Card.Content>
		</Card.Root>
	</div>

	<Card.Root class="mt-4 bg-muted/40">
		<Card.Header>
			<Card.Title class="text-base">Abgeleitete Werte</Card.Title>
			<Card.Description>
				Aus Versicherungsbeginn, Tarifwerk und Schadenshistorie — keine Werte des Kernsystems.
			</Card.Description>
		</Card.Header>
		<Card.Content class="grid gap-4 sm:grid-cols-3">
			{@render stat(
				'Staffeldeckel (kumuliert)',
				data.staffelLimit === null ? 'unbegrenzt' : euro(data.staffelLimit)
			)}
			{@render stat('Erstattet gesamt', euro(data.erstattungTotal))}
			{@render stat('Erstattet im laufenden Jahr', euro(data.erstattungCurrentYear))}
		</Card.Content>
		{#if data.staffelLimit !== null && data.staffelLimit > 0}
			{@const staffelUsage = Math.min(1, data.erstattungTotal / data.staffelLimit)}
			<Card.Content class="pt-0">
				<div class="flex items-center justify-between text-xs text-muted-foreground">
					<span>Staffel-Ausschöpfung</span>
					<span>{Math.round(staffelUsage * 100)} %</span>
				</div>
				<div class="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
					<div
						class="h-full rounded-full {staffelUsage >= 0.85 ? 'bg-akzent-verlauf' : 'bg-primary'}"
						style="width: {Math.max(2, staffelUsage * 100)}%"
					></div>
				</div>
			</Card.Content>
		{/if}
	</Card.Root>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Schadenshistorie
	</h2>
	<Card.Root>
		<Card.Header class="flex flex-row flex-wrap items-center justify-between gap-3">
			<div>
				<Card.Title class="text-base">Schadenshistorie</Card.Title>
				<Card.Description>
					{data.schadensfaelle.length === 1
						? 'Ein Schadensfall'
						: `${data.schadensfaelle.length} Schadensfälle`}
					{#if data.openSchadensfaelleCount > 0}&nbsp;· {data.openSchadensfaelleCount} offen{/if}
				</Card.Description>
			</div>
			<Button variant="outline" href="/kunden/{kunde.id}/schadensfaelle/neu">
				Schadensfall erfassen
			</Button>
		</Card.Header>
		<Card.Content>
			<SchadensfallTable
				schadensfaelle={data.schadensfaelle}
				onEdit={(schadensfall) => (editing = schadensfall)}
			/>
		</Card.Content>
	</Card.Root>
</section>

<SchadensfallEditDialog
	schadensfall={editing}
	error={form && 'editError' in form ? form.editError : undefined}
	calculation={form && 'calculated' in form && form.schadensfallId === editing?.id
		? form.calculated
		: null}
	onClose={() => (editing = null)}
/>
