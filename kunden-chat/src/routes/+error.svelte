<script lang="ts">
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button';
</script>

<div class="mx-auto mt-24 max-w-md text-center">
	<p class="text-6xl font-bold text-primary">{page.status}</p>
	<h1 class="mt-4 text-xl font-semibold">
		{page.status === 404 ? 'Diese Seite gibt es nicht' : 'Es ist ein Fehler aufgetreten'}
	</h1>
	<Button href="/" class="mt-6">Zurück zum Chat</Button>
</div>
