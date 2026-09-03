<script lang="ts">
	import { date } from '$lib/format';
	import type { VertragView } from '$lib/types/view';

	let { view }: { view: VertragView } = $props();

	const rows = $derived(
		[
			{ name: 'Tarif', value: view.anzeigename ?? view.tarif },
			{ name: 'Versichert seit', value: date(view.versicherungsbeginn) },
			{ name: 'Status', value: view.status },
			{
				name: 'Vorversicherung',
				value:
					typeof view.vorversicherung === 'boolean' ? (view.vorversicherung ? 'ja' : 'nein') : null
			},
			{
				name: 'Fehlende Zähne',
				value: typeof view.fehlendeZaehne === 'number' ? String(view.fehlendeZaehne) : null
			}
		].filter((row): row is { name: string; value: string } => Boolean(row.value))
	);
</script>

{#if rows.length > 0}
	<div class="p-3">
		<h3 class="mb-3 text-sm font-medium">
			{view.vorname ? `Ihr Vertrag, ${view.vorname}` : 'Ihr Vertrag'}
		</h3>
		<dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
			{#each rows as row (row.name)}
				<dt class="text-muted-foreground">{row.name}</dt>
				<dd>{row.value}</dd>
			{/each}
		</dl>
	</div>
{/if}
