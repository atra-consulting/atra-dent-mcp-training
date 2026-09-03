<script lang="ts">
	import './layout.css';
	import { Button } from '$lib/components/ui/button';

	let { data, children } = $props();
</script>

<div class="flex h-screen flex-col">
	<header class="flaeche-navy flex items-center justify-between gap-3 px-4 py-3">
		<a href="/" class="flex shrink-0 items-center gap-3">
			<img src="/atra-dent-logo.svg" alt="atra.dent" class="h-9 w-auto rounded bg-white p-1" />
		</a>
		{#if data.signedInCustomer}
			<div class="flex min-w-0 items-center gap-3 text-sm">
				<img
					src={data.signedInCustomer.avatar}
					alt=""
					class="size-8 shrink-0 rounded-full border border-white/30"
				/>
				<span class="min-w-0 truncate text-secondary">
					<span class="hidden sm:inline">Angemeldet als</span>
					{data.signedInCustomer.vorname}
					{data.signedInCustomer.nachname}
				</span>
				<form method="POST" action="/login?/abmelden" class="shrink-0">
					<Button type="submit" variant="secondary" size="sm">Abmelden</Button>
				</form>
			</div>
		{:else}
			<Button href="/login" variant="secondary" size="sm" class="shrink-0">Anmelden</Button>
		{/if}
	</header>
	<div class="min-h-0 flex-1 overflow-y-auto">{@render children()}</div>
</div>
