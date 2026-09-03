<script lang="ts">
	import KundeForm from '$lib/components/kunde-form.svelte';
	import PageHeader from '$lib/components/page-header.svelte';

	let { data, form } = $props();
</script>

<PageHeader
	variant="slim"
	eyebrow="Bestand"
	eyebrowHref="/kunden"
	title="Kunde anlegen"
	subline="Die Kundennummer vergibt das Kernsystem beim Speichern."
/>
<KundeForm tarife={data.tarife} error={form?.error} />
