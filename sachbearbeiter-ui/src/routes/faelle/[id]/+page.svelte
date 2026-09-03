<script lang="ts">
	import { enhance } from '$app/forms';
	import { resolve } from '$app/paths';
	import CircleCheck from '@lucide/svelte/icons/circle-check';
	import Hourglass from '@lucide/svelte/icons/hourglass';
	import Info from '@lucide/svelte/icons/info';
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import * as Alert from '$lib/components/ui/alert';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Textarea } from '$lib/components/ui/textarea';
	import PageHeader from '$lib/components/page-header.svelte';
	import StatusBadge from '$lib/components/status-badge.svelte';
	import BewertungCard from '$lib/components/bewertung-card.svelte';
	import Bearbeitungsprotokoll from '$lib/components/bearbeitungsprotokoll.svelte';
	import { ablehnungsgrund, date, euro, gozZustand } from '$lib/format';
	import { findingForPosition, orphanedFindings } from '$lib/positionen';
	import type { Ablehnungsgrund, Bewertungsempfehlung, GozZustand } from '$lib/types/api';
	import type { Outcome } from '$lib/types/schadensfallagent';

	let { data, form } = $props();
	const schadensfall = $derived(data.schadensfall);
	const kunde = $derived(data.kunde);

	const kundenName = $derived(
		kunde ? `${kunde.vorname} ${kunde.nachname}` : `Kunde ${schadensfall.kundenId}`
	);

	const ablehnungsgruende: Ablehnungsgrund[] = [
		'ANGERATEN',
		'KOSMETIK',
		'NICHT_APPROBIERT',
		'FEHLENDE_ZAEHNE',
		'NICHT_VERSICHERT',
		'WARTEZEIT',
		'SONSTIGES'
	];

	const canDecide = $derived(
		schadensfall.status !== 'abgelehnt' &&
			schadensfall.status !== 'ausgezahlt' &&
			schadensfall.status !== 'genehmigt'
	);
	const canPayOut = $derived(schadensfall.status === 'genehmigt');
	const canRecheck = $derived(
		schadensfall.status === 'eingereicht' ||
			schadensfall.status === 'in_pruefung' ||
			schadensfall.status === 'geprueft_freigabe' ||
			schadensfall.status === 'geprueft_eskalation'
	);

	function gozZustandVariant(zustand: GozZustand): 'default' | 'destructive' | 'secondary' {
		if (zustand === 'ENTHALTEN') return 'default';
		if (zustand === 'NICHT_ENTHALTEN') return 'destructive';
		return 'secondary';
	}

	let pruefungRunning = $state(false);

	function agentIcon(outcome: Outcome, empfehlung?: Bewertungsempfehlung) {
		if (outcome === 'laeuft') return Hourglass;
		if (outcome === 'weiterbearbeitet') return Info;
		if (outcome === 'gestoert' || outcome === 'abgelehnt') return TriangleAlert;
		if (empfehlung === 'freigabe') return CircleCheck;
		if (empfehlung === 'eskalation') return TriangleAlert;
		return Info;
	}

	function agentTitle(outcome: Outcome): string {
		if (outcome === 'geprueft') return 'Der Schadensfallagent hat geprüft';
		if (outcome === 'laeuft') return 'Die Prüfung läuft';
		if (outcome === 'weiterbearbeitet') return 'Der Fall wurde inzwischen weiterbearbeitet';
		if (outcome === 'abgelehnt') return 'Der Schadensfallagent hat nicht geprüft';
		return 'Der Schadensfallagent hat nicht geantwortet';
	}
</script>

{#snippet fact(label: string, value: string)}
	<div class="min-w-0">
		<p class="text-xs tracking-wide text-muted-foreground uppercase">{label}</p>
		<p class="mt-1 text-sm font-medium wrap-anywhere">{value}</p>
	</div>
{/snippet}

{#snippet subline()}
	<a href={resolve('/kunden/[id]', { id: String(schadensfall.kundenId) })} class="hover:underline">
		{kundenName}
	</a>
	· Behandlung am {date(schadensfall.behandlungsdatum)}
{/snippet}

<PageHeader eyebrow="Arbeitsvorrat" eyebrowHref="/faelle" title="Fall {schadensfall.id}" {subline}>
	<div class="mt-5 flex flex-wrap items-center gap-2">
		<StatusBadge status={schadensfall.status} />
	</div>
</PageHeader>

{#if form && 'error' in form}
	<Alert.Root variant="destructive" class="mb-6">
		<Alert.Title>Aktion fehlgeschlagen</Alert.Title>
		<Alert.Description>{form.error}</Alert.Description>
	</Alert.Root>
{/if}

{#if form?.agent}
	{@const agent = form.agent}
	{@const running = agent.outcome === 'laeuft'}
	{@const Icon = agentIcon(agent.outcome, agent.empfehlung)}
	<Alert.Root variant={agent.outcome === 'gestoert' ? 'destructive' : 'default'} class="mb-6">
		<Icon />
		<Alert.Title class="flex flex-wrap items-center gap-2">
			{agentTitle(agent.outcome)}
			{#if agent.empfehlung}
				<Badge variant={agent.empfehlung === 'freigabe' ? 'default' : 'secondary'}>
					{agent.empfehlung === 'freigabe' ? 'Freigabe empfohlen' : 'Eskalation'}
				</Badge>
			{/if}
		</Alert.Title>
		<Alert.Description>
			{agent.text}
			{#if running}
				<p class="mt-1">
					Sie müssen nichts tun: Sobald die Prüfung fertig ist, steht das Ergebnis in der Bewertung
					— ein Neuladen der Seite zeigt es. Ein erneuter Druck auf „Erneut prüfen lassen" stellt
					eine zweite Prüfung daneben, und eine der beiden verliert am Ende ihr Ergebnis.
				</p>
			{/if}
		</Alert.Description>
	</Alert.Root>
{/if}

<section>
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Rechnung
	</h2>
	<Card.Root>
		<Card.Content class="grid grid-cols-2 gap-4 sm:grid-cols-3">
			{@render fact('Rechnungsbetrag', euro(schadensfall.rechnungsbetrag))}
			{#if schadensfall.erstattungsbetrag != null}
				{@render fact('Erstattungsbetrag', euro(schadensfall.erstattungsbetrag))}
			{/if}
			{#if schadensfall.ablehnungsgrund}
				{@render fact('Ablehnungsgrund', ablehnungsgrund(schadensfall.ablehnungsgrund))}
			{/if}
			{#if schadensfall.ablehnungshinweis}
				{@render fact('Hinweis', schadensfall.ablehnungshinweis)}
			{/if}
			{#if schadensfall.rechnung}
				{@render fact('Rechnungsnummer', schadensfall.rechnung.rechnungsnummer ?? '—')}
				{@render fact('Rechnungsdatum', date(schadensfall.rechnung.rechnungsdatum))}
				{@render fact('Absender', schadensfall.rechnung.absender ?? '—')}
				{@render fact('Patient', schadensfall.rechnung.patient ?? '—')}
				{@render fact('Gesamtbetrag laut Rechnung', euro(schadensfall.rechnung.gesamtbetrag))}
			{:else}
				<p class="col-span-full text-sm text-muted-foreground">Keine Rechnungsdaten erfasst.</p>
			{/if}
		</Card.Content>
	</Card.Root>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Positionen
	</h2>
	<Card.Root>
		<Card.Content>
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>GOZ</Table.Head>
						<Table.Head>Beschreibung</Table.Head>
						<Table.Head>Zahn</Table.Head>
						<Table.Head>Datum</Table.Head>
						<Table.Head>Bereich</Table.Head>
						<Table.Head class="text-right">Betrag</Table.Head>
						<Table.Head>Zustand</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each schadensfall.positionen as position, index (index)}
						{@const finding = findingForPosition(schadensfall.bewertung, position, index)}
						<Table.Row>
							<Table.Cell>
								{#if position.goz}
									{position.goz}
								{:else}
									<span
										class="text-muted-foreground"
										title="Material, Labor oder Verlangensleistung">ohne Ziffer</span
									>
								{/if}
							</Table.Cell>
							<Table.Cell>{position.beschreibung}</Table.Cell>
							<Table.Cell>{position.zahn ?? '—'}</Table.Cell>
							<Table.Cell>{position.datum ? date(position.datum) : '—'}</Table.Cell>
							<Table.Cell>{position.leistungsbereich ?? '—'}</Table.Cell>
							<Table.Cell class="text-right">{euro(position.betrag)}</Table.Cell>
							<Table.Cell>
								{#if finding}
									<Badge variant={gozZustandVariant(finding.zustand)} title={finding.begruendung}>
										{gozZustand(finding.zustand)}
									</Badge>
								{:else}
									—
								{/if}
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
			{#if orphanedFindings(schadensfall.bewertung, schadensfall.positionen).length > 0}
				<Alert.Root variant="destructive" class="mt-4">
					<TriangleAlert class="size-4" />
					<Alert.Title>Befunde ohne Position</Alert.Title>
					<Alert.Description>
						Die Bewertung nennt Befunde, die auf keine Position dieses Falls zeigen. Sie gehören zu
						keiner Zeile der Tabelle:
						<ul class="mt-2 list-disc pl-5">
							{#each orphanedFindings(schadensfall.bewertung, schadensfall.positionen) as finding, i (i)}
								<li>
									{finding.index != null ? `Position ${finding.index + 1}` : 'ohne Stelle'}
									{finding.goz ? ` · GOZ ${finding.goz}` : ''} · {gozZustand(finding.zustand)}
									— {finding.begruendung}
								</li>
							{/each}
						</ul>
					</Alert.Description>
				</Alert.Root>
			{/if}
		</Card.Content>
	</Card.Root>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Bewertung
	</h2>
	<BewertungCard
		bewertung={schadensfall.bewertung}
		rechnungsbetrag={schadensfall.rechnungsbetrag}
	/>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Bearbeitungsprotokoll
	</h2>
	<Card.Root>
		<Card.Content>
			<Bearbeitungsprotokoll entries={schadensfall.bearbeitungsprotokoll} />
		</Card.Content>
		<Card.Footer class="justify-end">
			<Button variant="link" size="sm" href="/faelle/{schadensfall.id}/protokoll">
				Vollständige Agentenaufrufe →
			</Button>
		</Card.Footer>
	</Card.Root>
</section>

<section class="mt-8">
	<h2 class="mb-3 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
		Aktionen
	</h2>
	<div class="grid gap-4 lg:grid-cols-2">
		{#if canDecide}
			<Card.Root>
				<Card.Header>
					<Card.Title class="text-base">Genehmigen</Card.Title>
				</Card.Header>
				<Card.Content>
					<form method="POST" action="?/genehmigen" use:enhance class="grid gap-3">
						<div class="grid gap-1.5">
							<Label for="erstattungsbetrag">Erstattungsbetrag</Label>
							<Input
								id="erstattungsbetrag"
								name="erstattungsbetrag"
								value={schadensfall.bewertung?.erstattungsvorschlag ?? ''}
							/>
						</div>
						<div>
							<Button type="submit">Genehmigen</Button>
						</div>
					</form>
				</Card.Content>
			</Card.Root>

			<Card.Root>
				<Card.Header>
					<Card.Title class="text-base">Ablehnen</Card.Title>
				</Card.Header>
				<Card.Content>
					<form method="POST" action="?/ablehnen" use:enhance class="grid gap-3">
						<div class="grid gap-1.5">
							<Label for="ablehnungsgrund">Ablehnungsgrund</Label>
							<select
								id="ablehnungsgrund"
								name="ablehnungsgrund"
								required
								class="h-9 rounded-md border border-input bg-background px-3 text-sm"
							>
								<option value="" disabled selected>bitte wählen …</option>
								{#each ablehnungsgruende as grund (grund)}
									<option value={grund}>{ablehnungsgrund(grund)}</option>
								{/each}
							</select>
						</div>
						<div class="grid gap-1.5">
							<Label for="ablehnungshinweis">Hinweis (Freitext)</Label>
							<Textarea id="ablehnungshinweis" name="ablehnungshinweis" />
						</div>
						<div>
							<Button type="submit" variant="destructive">Ablehnen</Button>
						</div>
					</form>
				</Card.Content>
			</Card.Root>
		{/if}

		{#if canPayOut}
			<Card.Root>
				<Card.Header>
					<Card.Title class="text-base">Auszahlen</Card.Title>
				</Card.Header>
				<Card.Content>
					<form method="POST" action="?/auszahlen" use:enhance>
						<Button type="submit">Auszahlen</Button>
					</form>
				</Card.Content>
			</Card.Root>
		{/if}

		{#if canRecheck}
			<Card.Root>
				<Card.Header>
					<Card.Title class="text-base">Erneut prüfen lassen</Card.Title>
					<Card.Description>
						{#if schadensfall.status === 'eingereicht'}
							Der Fall steht schon auf „eingereicht" und ist damit zur Prüfung freigegeben; der
							Zuruf spart nur das Warten auf den nächsten Durchlauf des Agenten. Ist dessen Takt
							abgeschaltet, ist er der einzige Weg zu einer Prüfung.
						{:else}
							Setzt den Fall auf „eingereicht" und stößt den Schadensfallagenten unmittelbar an.
							Läuft schon eine Prüfung, stellt das eine zweite daneben — eine der beiden verliert am
							Ende ihr Ergebnis. Warten Sie in dem Fall lieber, bis die Bewertung erscheint.
						{/if}
					</Card.Description>
				</Card.Header>
				<Card.Content>
					<form
						method="POST"
						action="?/erneutPruefen"
						use:enhance={() => {
							pruefungRunning = true;
							return async ({ update }) => {
								await update();
								pruefungRunning = false;
							};
						}}
					>
						<Button type="submit" variant="outline" disabled={pruefungRunning}>
							Erneut prüfen lassen
						</Button>
						{#if pruefungRunning}
							<p class="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
								<Hourglass class="size-4" />
								Der Agent prüft … die Antwort kommt nach spätestens 30 Sekunden.
							</p>
						{/if}
					</form>
				</Card.Content>
			</Card.Root>
		{/if}

		{#if !canDecide && !canPayOut && !canRecheck}
			<p class="text-sm text-muted-foreground">Keine Aktionen verfügbar.</p>
		{/if}
	</div>
</section>
