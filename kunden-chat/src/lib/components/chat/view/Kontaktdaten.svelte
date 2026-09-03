<script lang="ts">
	import type { KontaktdatenView } from '$lib/types/view';

	let { view }: { view: KontaktdatenView } = $props();

	const anschrift = $derived(
		[view.adresse?.strasse, [view.adresse?.plz, view.adresse?.ort].filter(Boolean).join(' ')]
			.filter(Boolean)
			.join(', ')
	);

	const rows = $derived(
		[
			{ name: 'Anschrift', value: anschrift },
			{ name: 'E-Mail', value: view.email },
			{ name: 'Telefon', value: view.telefon }
		].filter((row): row is { name: string; value: string } => Boolean(row.value))
	);
</script>

{#if rows.length > 0}
	<div class="p-3">
		<h3 class="mb-3 text-sm font-medium">
			{view.vorname ? `Ihre Kontaktdaten, ${view.vorname}` : 'Ihre Kontaktdaten'}
		</h3>
		<dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
			{#each rows as row (row.name)}
				<dt class="text-muted-foreground">{row.name}</dt>
				<dd>{row.value}</dd>
			{/each}
		</dl>
		<p class="mt-3 text-xs text-muted-foreground">So steht es jetzt in Ihrer Akte.</p>
	</div>
{/if}
