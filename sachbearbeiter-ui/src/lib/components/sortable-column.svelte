<script lang="ts">
	import * as Table from '$lib/components/ui/table';
	import { cn } from '$lib/utils';
	import ArrowDown from '@lucide/svelte/icons/arrow-down';
	import ArrowUp from '@lucide/svelte/icons/arrow-up';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import type { Snippet } from 'svelte';

	let {
		column,
		activeColumn,
		direction,
		onsort,
		align = 'left',
		class: className,
		children
	}: {
		column: string;
		activeColumn: string;
		direction: 'asc' | 'desc';
		onsort: (column: string) => void;
		align?: 'left' | 'right';
		class?: string;
		children: Snippet;
	} = $props();

	const active = $derived(column === activeColumn);
</script>

<Table.Head
	class={cn('p-0', className)}
	aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}
>
	<button
		type="button"
		onclick={() => onsort(column)}
		class={cn(
			'group flex h-10 w-full items-center gap-1.5 px-2 transition-colors hover:text-primary focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset',
			align === 'right' && 'justify-end',
			active && 'text-primary'
		)}
	>
		{@render children()}
		{#if active}
			{#if direction === 'asc'}
				<ArrowUp class="size-3.5 shrink-0" aria-hidden="true" />
			{:else}
				<ArrowDown class="size-3.5 shrink-0" aria-hidden="true" />
			{/if}
		{:else}
			<ChevronsUpDown
				class="size-3.5 shrink-0 opacity-25 transition-opacity group-hover:opacity-70 group-focus-visible:opacity-70"
				aria-hidden="true"
			/>
		{/if}
	</button>
</Table.Head>
