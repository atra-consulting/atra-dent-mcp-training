<script lang="ts">
	import { cn } from '$lib/utils';
	import ChevronLeft from '@lucide/svelte/icons/chevron-left';
	import type { Snippet } from 'svelte';

	let {
		eyebrow,
		eyebrowHref,
		title,
		subline,
		variant = 'default',
		media,
		actions,
		children,
		class: className
	}: {
		eyebrow: string;
		eyebrowHref?: string;
		title: string;
		subline?: string | Snippet;
		variant?: 'default' | 'slim';
		media?: Snippet;
		actions?: Snippet;
		children?: Snippet;
		class?: string;
	} = $props();
</script>

<header
	class={cn(
		'flaeche-navy mb-6 rounded-xl text-white',
		variant === 'slim' ? 'px-5 py-4 md:px-7 md:py-5' : 'px-5 py-5 md:px-7 md:py-6',
		className
	)}
>
	<div class="flex flex-wrap items-start justify-between gap-4">
		<div class="flex min-w-0 items-center gap-4">
			{#if media}{@render media()}{/if}
			<div class="min-w-0">
				{#if eyebrowHref}
					<a
						href={eyebrowHref}
						class="-ml-1.5 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-xs font-medium tracking-[0.2em] text-secondary uppercase transition-colors hover:bg-white/10 hover:text-white focus-visible:ring-2 focus-visible:ring-white/50 focus-visible:outline-none"
					>
						<ChevronLeft class="size-3.5" aria-hidden="true" />
						{eyebrow}
					</a>
				{:else}
					<p class="text-xs font-medium tracking-[0.2em] text-secondary uppercase">{eyebrow}</p>
				{/if}
				<h1
					class={cn(
						'mt-1 font-semibold',
						variant === 'slim' ? 'text-xl md:text-2xl' : 'text-2xl md:text-3xl'
					)}
				>
					{title}
				</h1>
				{#if subline}
					<p class="mt-1 text-sm text-white/70">
						{#if typeof subline === 'string'}
							{subline}
						{:else}
							{@render subline()}
						{/if}
					</p>
				{/if}
			</div>
		</div>
		{#if actions}
			<div class="flex flex-wrap items-center gap-2">{@render actions()}</div>
		{/if}
	</div>
	{#if children}{@render children()}{/if}
</header>
