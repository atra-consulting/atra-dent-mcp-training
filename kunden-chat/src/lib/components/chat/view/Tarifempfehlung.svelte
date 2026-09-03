<script lang="ts">
	import type { TarifempfehlungView } from '$lib/types/view';

	let { view }: { view: TarifempfehlungView } = $props();

	const empfehlungen = $derived(view.empfehlungen ?? []);
	const ownTarif = $derived(view.vertragstarif?.schluessel ?? null);
</script>

{#if empfehlungen.length > 0}
	<div class="p-3">
		<h3 class="mb-3 text-sm font-medium">Empfohlene Tarife</h3>
		<ol class="space-y-2">
			{#each empfehlungen as empfehlung, index (index)}
				<li class="flex items-center gap-3">
					<span
						class="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-medium text-primary-foreground"
						aria-hidden="true"
					>
						{empfehlung.rang ?? '·'}
					</span>
					<span class="text-sm">
						<span class="sr-only">Platz {empfehlung.rang ?? '–'}:</span>
						{empfehlung.anzeigename ?? empfehlung.tarif ?? '—'}
						{#if empfehlung.tarif !== undefined && empfehlung.tarif === ownTarif}
							<span class="text-xs text-akzent">— Ihr Tarif</span>
						{/if}
					</span>
				</li>
			{/each}
		</ol>
	</div>
{/if}
