<script lang="ts">
	import { asNumber, currency } from '$lib/format';
	import type { BeitragsvergleichView } from '$lib/types/view';

	let { view }: { view: BeitragsvergleichView } = $props();

	const ownTarif = $derived(view.vertragstarif?.schluessel ?? null);

	const bars = $derived.by(() => {
		const values = (view.beitraege ?? [])
			.map((beitrag) => ({
				tarif: beitrag.tarif,
				name: beitrag.anzeigename ?? beitrag.tarif ?? '—',
				amount: asNumber(beitrag.monatsbeitrag),
				display: currency(beitrag.monatsbeitrag)
			}))
			.filter((value) => value.amount !== null && value.display !== null);

		const largest = Math.max(...values.map((value) => value.amount ?? 0), 0);
		return values.map((value) => ({
			...value,
			share: largest > 0 ? Math.max(((value.amount ?? 0) / largest) * 100, 4) : 0,
			own: value.tarif !== undefined && value.tarif === ownTarif
		}));
	});

	const highlighted = $derived(bars.find((entry) => entry.own) ?? null);
</script>

{#if bars.length > 0}
	<div class="p-3">
		<h3 class="mb-3 text-sm font-medium">Monatsbeitrag im Vergleich</h3>
		<dl class="space-y-2">
			{#each bars as entry, index (index)}
				<div class="grid grid-cols-[minmax(6rem,10rem)_1fr] items-center gap-3">
					<dt class="truncate text-xs" title={entry.name}>
						{entry.name}
						{#if entry.own}
							<span class="block text-[0.7rem] text-akzent">Ihr Tarif</span>
						{/if}
					</dt>
					<dd class="flex items-center gap-2">
						<div
							class="h-5 rounded-sm {entry.own ? 'bg-akzent' : 'bg-primary'}"
							style="width: {entry.share}%"
						></div>
						<span class="text-xs whitespace-nowrap tabular-nums">{entry.display}</span>
					</dd>
				</div>
			{/each}
		</dl>
		{#if highlighted}
			<p class="mt-3 text-xs text-muted-foreground">
				Hervorgehoben ist {highlighted.name} — Ihr Tarif.
			</p>
		{:else if ownTarif}
			<p class="mt-3 text-xs text-muted-foreground">
				Ihr Tarif {view.vertragstarif?.anzeigename ?? ownTarif} ist hier nicht berechnet.
			</p>
		{/if}
	</div>
{/if}
