<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';

	let { data } = $props();
</script>

<div class="mx-auto mt-16 max-w-md">
	<h1 class="mb-2 text-2xl font-semibold">Demo-Anmeldung</h1>
	<p class="mb-6 text-sm text-muted-foreground">
		Wählen Sie einen Demo-Kunden, um Fragen zu Vertrag und Schadensfällen zu stellen. Eine echte
		Anmeldung gibt es in diesem Lab nicht; die Kunden kommen aus dem Kernsystem.
	</p>
	{#if data.coreSystemError}
		<p
			class="mb-4 rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive"
		>
			Das Kernsystem ist nicht erreichbar — die Demo-Anmeldung ist gerade nicht möglich.
		</p>
	{/if}
	<div class="space-y-3">
		{#each data.selection as entry (entry.id)}
			<Card.Root>
				<Card.Content class="flex items-center justify-between gap-3 p-4">
					<div class="flex min-w-0 items-center gap-3">
						<img src={entry.avatar} alt="" class="size-11 shrink-0 rounded-full" loading="lazy" />
						<div class="min-w-0">
							<p class="truncate font-medium">{entry.name}</p>
							<Badge variant="secondary" class="mt-1">{entry.tarifId}</Badge>
						</div>
					</div>
					<form method="POST" action="?/anmelden">
						<input type="hidden" name="kundenId" value={entry.id} />
						<Button type="submit">Anmelden</Button>
					</form>
				</Card.Content>
			</Card.Root>
		{/each}
	</div>
	<a href="/" class="mt-6 inline-block text-sm text-primary hover:underline">Zurück zum Chat</a>
</div>
