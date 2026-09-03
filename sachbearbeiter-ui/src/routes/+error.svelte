<script lang="ts">
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button';
</script>

<div class="mx-auto mt-24 max-w-md text-center">
	<p class="text-6xl font-bold text-primary">{page.status}</p>
	<h1 class="mt-4 text-xl font-semibold">
		{page.status === 404 ? 'Nicht gefunden' : 'Es ist ein Fehler aufgetreten'}
	</h1>
	<p class="mt-2 text-sm text-muted-foreground">{page.error?.message}</p>
	<Button href="/kunden" class="mt-6">Zur Kundenliste</Button>
</div>
