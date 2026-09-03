<script lang="ts">
	import { cn } from '$lib/utils.js';
	import type { HTMLInputAttributes } from 'svelte/elements';

	let {
		ref = $bindable(null),
		checked = $bindable(false),
		class: className,
		...restProps
	}: Omit<HTMLInputAttributes, 'type'> & {
		ref?: HTMLInputElement | null;
		checked?: boolean;
	} = $props();
</script>

<input
	bind:this={ref}
	bind:checked
	type="checkbox"
	data-slot="checkbox"
	class={cn(
		'size-4 shrink-0 cursor-pointer rounded-[4px] accent-primary',
		'focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
		'disabled:cursor-not-allowed disabled:opacity-50',
		className
	)}
	{...restProps}
/>
