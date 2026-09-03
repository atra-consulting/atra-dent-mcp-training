<script lang="ts">
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { dateTime, euro, eskalationsgrund } from '$lib/format';
	import type { Bewertung, Geldbetrag } from '$lib/types/api';

	let { bewertung, rechnungsbetrag }: { bewertung: Bewertung | null; rechnungsbetrag: Geldbetrag } =
		$props();
</script>

{#snippet section(label: string)}
	<p class="text-xs font-semibold tracking-widest text-muted-foreground uppercase">{label}</p>
{/snippet}

<Card.Root>
	<Card.Header>
		<Card.Title class="text-base">Bewertung</Card.Title>
	</Card.Header>
	<Card.Content class="space-y-4">
		{#if !bewertung}
			<p class="text-sm text-muted-foreground">Noch nicht geprüft</p>
		{:else}
			<div class="flex flex-wrap items-center gap-3">
				<Badge variant={bewertung.empfehlung === 'freigabe' ? 'default' : 'secondary'}>
					{bewertung.empfehlung === 'freigabe' ? 'Freigabe empfohlen' : 'Eskalation'}
				</Badge>
				{#if bewertung.erstattungsvorschlag}
					<p class="text-sm">
						<span class="font-semibold text-primary">
							{euro(bewertung.erstattungsvorschlag)}
						</span>
						<span class="text-xs text-muted-foreground">
							Vorschlag · Rechnungsbetrag {euro(rechnungsbetrag)}
						</span>
					</p>
				{/if}
			</div>

			{#if bewertung.eskalationsgruende.length > 0}
				<div>
					{@render section('Eskalationsgründe')}
					<ul class="mt-1.5 list-disc space-y-1 pl-5 text-sm">
						{#each bewertung.eskalationsgruende as entry, index (index)}
							<li>
								<span class="font-semibold">{eskalationsgrund(entry.code)}</span>
								{entry.text}
							</li>
						{/each}
					</ul>
				</div>
			{/if}

			{#if bewertung.arztauskunft}
				{@const arztauskunft = bewertung.arztauskunft}
				<div>
					{@render section('Arztauskunft')}
					<div class="mt-1.5 flex flex-wrap items-center gap-2">
						<Badge
							variant="outline"
							class={arztauskunft.plausibilitaet === 'auffaellig'
								? 'border-destructive text-destructive'
								: ''}
						>
							{arztauskunft.plausibilitaet === 'auffaellig'
								? 'Plausibilität auffällig'
								: 'Plausibel'}
						</Badge>
						<Badge
							variant="outline"
							class={arztauskunft.notwendigkeit === 'fraglich'
								? 'border-destructive text-destructive'
								: ''}
						>
							{arztauskunft.notwendigkeit === 'fraglich' ? 'Notwendigkeit fraglich' : 'Üblich'}
						</Badge>
					</div>
					<p class="mt-1.5 text-sm">{arztauskunft.text}</p>
					{#if arztauskunft.hinweis}
						<p class="mt-1.5 flex items-start gap-1.5 text-sm text-muted-foreground">
							<TriangleAlert class="mt-0.5 size-4 shrink-0" aria-hidden="true" />
							{arztauskunft.hinweis}
						</p>
					{/if}
				</div>
			{/if}

			<div>
				{@render section('Begründung')}
				<p class="mt-1.5 text-sm">{bewertung.begruendung}</p>
			</div>
		{/if}
	</Card.Content>
	{#if bewertung}
		<Card.Footer class="text-xs text-muted-foreground">
			{[bewertung.agent, bewertung.modell, dateTime(bewertung.zeitpunkt)]
				.filter(Boolean)
				.join(' · ')}
		</Card.Footer>
	{/if}
</Card.Root>
