<script lang="ts">
	import { enhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import * as Alert from '$lib/components/ui/alert';
	import PageHeader from '$lib/components/page-header.svelte';
	import type { Dokument } from '$lib/types/wissen';

	let { data, form } = $props();

	let searching = $state(false);

	const artLabels: Record<Dokument['art'], string> = {
		BEDINGUNGSWERK: 'Bedingungswerk',
		TARIFVERGLEICH: 'Tarifvergleich',
		GOZ_ZUORDNUNG: 'GOZ-Zuordnung',
		BERATUNGSHANDBUCH: 'Beratungshandbuch'
	};

	const bedingungswerkDocuments = $derived(
		data.documents.filter((d) => d.art === 'BEDINGUNGSWERK')
	);
	const otherDocuments = $derived(data.documents.filter((d) => d.art !== 'BEDINGUNGSWERK'));

	function tarifName(id: string | null | undefined): string {
		return data.tarife.find((t) => t.id === id)?.name ?? id ?? 'tarifübergreifend';
	}
</script>

<PageHeader
	variant="slim"
	eyebrow="Bedingungswerke"
	title="Wissensdatenbank"
	subline="Semantische Suche in den Bedingungswerken — jede Fundstelle ist auf den Paragraphen genau zitierfähig."
/>

{#if !data.wissenAvailable}
	<Alert.Root variant="destructive" class="mb-6">
		<Alert.Title>Wissensdienst nicht erreichbar</Alert.Title>
		<Alert.Description>
			Der Dienst auf Port 8082 antwortet nicht. Starten: <code>./start.sh wissen</code>
		</Alert.Description>
	</Alert.Root>
{/if}

<Card.Root class="mb-6">
	<Card.Content>
		<form
			method="POST"
			action="?/search"
			use:enhance={() => {
				searching = true;
				return async ({ update }) => {
					searching = false;
					await update();
				};
			}}
			class="flex flex-wrap items-end gap-3"
		>
			<div class="grid gap-1.5">
				<Label for="tarif">Tarif</Label>
				<select
					id="tarif"
					name="tarif"
					required
					class="h-9 rounded-md border border-input bg-background px-3 text-sm"
				>
					<option value="ALLE">Alle Tarife</option>
					{#each data.tarife as tarif (tarif.id)}
						<option value={tarif.id}>{tarif.name}</option>
					{/each}
				</select>
			</div>
			<div class="grid min-w-64 flex-1 gap-1.5">
				<Label for="frage">Frage</Label>
				<Input
					id="frage"
					name="frage"
					placeholder="z. B. Wie lange ist die Wartezeit für Zahnersatz?"
					value={form && 'fachfrage' in form ? (form.fachfrage ?? '') : ''}
					required
				/>
			</div>
			<Button
				type="submit"
				class="bg-akzent-verlauf border-0 hover:opacity-90"
				disabled={searching}
			>
				{searching ? 'Suche …' : 'Suchen'}
			</Button>
		</form>
		{#if form && 'searchError' in form}
			<p class="mt-2 text-sm text-destructive">{form.searchError}</p>
		{/if}
	</Card.Content>
</Card.Root>

{#if form && 'hits' in form && form.hits}
	{@const hits = form.hits}
	<div class="mb-10 space-y-4">
		<p class="text-sm text-muted-foreground">
			{hits.length === 0
				? 'Keine hinreichend ähnliche Passage gefunden — das ist ein gültiges Ergebnis: Zu dieser Frage sagen die Bedingungswerke nichts.'
				: `${hits.length} Fundstellen zu „${form.fachfrage}“ ${
						form.scope === 'ALLE'
							? 'über alle Bedingungswerke (brillant mit Selbstbehalt teilt das Werk mit brillant)'
							: `in ${tarifName(form.scope)}`
					}:`}
		</p>
		{#if form.partialError}
			<p class="text-sm text-destructive">Teilweise fehlgeschlagen: {form.partialError}</p>
		{/if}
		{#each hits as hit (hit.tarif + hit.dokumentId + hit.abschnittId + hit.bewertung)}
			<Card.Root>
				<Card.Header class="flex flex-row items-start justify-between gap-4 pb-2">
					<div>
						<Card.Title class="text-base text-primary">{hit.ueberschrift}</Card.Title>
						<Badge variant="secondary" class="mt-1.5">{tarifName(hit.tarif)}</Badge>
					</div>
					<div class="shrink-0 text-right">
						<span class="text-xs font-medium text-muted-foreground">
							{Math.round(hit.bewertung * 100)} % passend
						</span>
						<div class="mt-1 h-1.5 w-24 overflow-hidden rounded-full bg-muted">
							<div class="h-full bg-primary" style="width: {hit.bewertung * 100}%"></div>
						</div>
					</div>
				</Card.Header>
				<Card.Content>
					<blockquote
						class="border-l-4 border-secondary bg-muted/40 p-3 text-sm whitespace-pre-wrap"
					>
						{hit.text}
					</blockquote>
					<div class="mt-3 flex flex-wrap items-center justify-between gap-2">
						<span class="text-xs text-muted-foreground">{hit.dokumentId} · {hit.abschnittId}</span>
						<div class="flex gap-2">
							<Button
								variant="outline"
								size="sm"
								href="/wissen{hit.htmlUrl}"
								target="_blank"
								rel="noopener"
							>
								Im Volltext öffnen
							</Button>
							<Button
								variant="outline"
								size="sm"
								href="/wissen{hit.pdfUrl}"
								target="_blank"
								rel="noopener"
							>
								PDF
							</Button>
						</div>
					</div>
				</Card.Content>
			</Card.Root>
		{/each}
	</div>
{/if}

{#if data.documents.length > 0}
	<h2 class="mb-3 text-lg font-semibold">Dokumente</h2>
	<div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
		{#each [...bedingungswerkDocuments, ...otherDocuments] as doc (doc.dokumentId)}
			<Card.Root>
				<Card.Header class="pb-2">
					<Card.Description class="flex items-center gap-2">
						{artLabels[doc.art]}
						{#if doc.vertraulichkeit === 'INTERN'}
							<Badge variant="outline" class="border-akzent/40 text-akzent">INTERN</Badge>
						{/if}
					</Card.Description>
					<Card.Title class="text-sm leading-snug">{doc.titel}</Card.Title>
				</Card.Header>
				<Card.Content class="flex items-center justify-between gap-2">
					<span class="text-xs text-muted-foreground">
						{doc.tarif ? tarifName(doc.tarif) : 'tarifübergreifend'}
					</span>
					<div class="flex gap-2">
						<Button
							variant="outline"
							size="sm"
							href="/wissen{doc.pdfUrl}"
							target="_blank"
							rel="noopener"
						>
							PDF
						</Button>
						<Button
							variant="outline"
							size="sm"
							href="/wissen{doc.htmlUrl}"
							target="_blank"
							rel="noopener"
						>
							HTML
						</Button>
					</div>
				</Card.Content>
			</Card.Root>
		{/each}
	</div>
{/if}
