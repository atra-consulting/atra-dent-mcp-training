<script lang="ts">
	import './layout.css';
	import { afterNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import BookOpen from '@lucide/svelte/icons/book-open';
	import Inbox from '@lucide/svelte/icons/inbox';
	import Scale from '@lucide/svelte/icons/scale';
	import Users from '@lucide/svelte/icons/users';
	let { children } = $props();

	let content = $state<HTMLElement | null>(null);

	afterNavigate((nav) => {
		if (nav.type === 'enter' || nav.to?.url.hash) return;
		content?.scrollTo({ top: 0, left: 0 });
	});

	const navigation = [
		{ href: '/kunden', label: 'Kunden', icon: Users },
		{ href: '/faelle', label: 'Offene Fälle', icon: Inbox },
		{ href: '/tarifvergleich', label: 'Tarifvergleich', icon: Scale },
		{ href: '/wissen', label: 'Wissen', icon: BookOpen }
	];
</script>

<div class="flex h-screen flex-col md:flex-row">
	<header class="flaeche-navy flex shrink-0 items-center justify-center px-4 py-2.5 md:hidden">
		<a href="/kunden" aria-label="Zur Startseite">
			<img src="/atra-dent-logo.svg" alt="atra.dent" class="h-9 w-auto rounded bg-white p-1" />
		</a>
	</header>

	<aside
		class="flaeche-navy hidden w-60 shrink-0 flex-col overflow-y-auto text-white md:flex xl:w-72"
	>
		<div class="px-6 py-6">
			<img src="/atra-dent-logo.svg" alt="atra.dent" class="w-full rounded-md bg-white p-2" />
		</div>
		<nav class="flex flex-col gap-1 px-4">
			{#each navigation as entry (entry.href)}
				{@const Icon = entry.icon}
				<a
					href={entry.href}
					class="flex items-center gap-3 rounded-md border-l-2 px-4 py-2.5 text-sm transition-colors
						{page.url.pathname.startsWith(entry.href)
						? 'border-akzent-hell bg-white/10 font-medium text-white'
						: 'border-transparent text-secondary hover:bg-white/5 hover:text-white'}"
				>
					<Icon class="size-4 shrink-0" aria-hidden="true" />
					{entry.label}
				</a>
			{/each}
		</nav>
	</aside>

	<main bind:this={content} class="min-w-0 flex-1 overflow-y-auto p-4 md:p-6 xl:p-8">
		{@render children()}
	</main>

	<nav class="flaeche-navy flex shrink-0 md:hidden" aria-label="Hauptnavigation">
		{#each navigation as entry (entry.href)}
			{@const Icon = entry.icon}
			<a
				href={entry.href}
				class="flex min-w-0 flex-1 flex-col items-center gap-1 border-t-2 px-1 py-2 text-[11px]
					{page.url.pathname.startsWith(entry.href)
					? 'border-akzent-hell bg-white/10 font-medium text-white'
					: 'border-transparent text-secondary'}"
			>
				<Icon class="size-5 shrink-0" aria-hidden="true" />
				<span class="max-w-full truncate">{entry.label}</span>
			</a>
		{/each}
	</nav>
</div>
