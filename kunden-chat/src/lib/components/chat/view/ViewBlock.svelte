<script lang="ts">
	import type { View } from '$lib/types/view';
	import Beitragsvergleich from './Beitragsvergleich.svelte';
	import Hymne from './Hymne.svelte';
	import Kontaktdaten from './Kontaktdaten.svelte';
	import Schadensfaelle from './Schadensfaelle.svelte';
	import Tarifempfehlung from './Tarifempfehlung.svelte';
	import Tarifvergleich from './Tarifvergleich.svelte';
	import Vertrag from './Vertrag.svelte';

	let { view }: { view: View } = $props();
</script>

<section
	class="mt-2 w-full overflow-hidden rounded-lg bg-card ring-1 ring-foreground/10"
	aria-label="Angaben zur Antwort"
>
	{#if view.kind === 'tarifvergleich'}
		<Tarifvergleich {view} />
	{:else if view.kind === 'beitragsvergleich'}
		<Beitragsvergleich {view} />
	{:else if view.kind === 'tarifempfehlung'}
		<Tarifempfehlung {view} />
	{:else if view.kind === 'vertrag'}
		<Vertrag {view} />
	{:else if view.kind === 'kontaktdaten'}
		<Kontaktdaten {view} />
	{:else if view.kind === 'schadensfaelle'}
		<Schadensfaelle {view} />
	{:else if view.kind === 'hymne'}
		<Hymne />
	{/if}
</section>
