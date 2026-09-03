<script lang="ts">
	import KundeForm from '$lib/components/kunde-form.svelte';
	import PageHeader from '$lib/components/page-header.svelte';

	let { data, form } = $props();
</script>

<PageHeader
	variant="slim"
	eyebrow="Akte {data.kunde.id}"
	eyebrowHref="/kunden/{data.kunde.id}"
	title="Stammdaten bearbeiten"
	subline="{data.kunde.nachname}, {data.kunde.vorname}"
/>
<KundeForm kunde={data.kunde} tarife={data.tarife} error={form?.error} />
