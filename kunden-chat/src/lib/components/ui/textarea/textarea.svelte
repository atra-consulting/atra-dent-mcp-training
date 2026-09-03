<script lang="ts">
	import { cn, type WithElementRef } from '$lib/utils.js';
	import type { HTMLTextareaAttributes } from 'svelte/elements';

	type Props = WithElementRef<HTMLTextareaAttributes, HTMLTextAreaElement> & {
		maxRows?: number;
	};

	let {
		ref = $bindable(null),
		value = $bindable(),
		class: className,
		maxRows = 6,
		'data-slot': dataSlot = 'textarea',
		...restProps
	}: Props = $props();

	function fitHeight(field: HTMLTextAreaElement) {
		const computed = getComputedStyle(field);
		const lineHeight = Number.parseFloat(computed.lineHeight) || 20;
		const frame =
			Number.parseFloat(computed.paddingTop) +
			Number.parseFloat(computed.paddingBottom) +
			Number.parseFloat(computed.borderTopWidth) +
			Number.parseFloat(computed.borderBottomWidth);

		field.style.height = 'auto';
		const limit = lineHeight * maxRows + frame;
		field.style.height = `${Math.min(field.scrollHeight, limit)}px`;
		field.style.overflowY = field.scrollHeight > limit ? 'auto' : 'hidden';
	}

	$effect(() => {
		void value;
		if (ref) fitHeight(ref);
	});
</script>

<textarea
	bind:this={ref}
	data-slot={dataSlot}
	rows="1"
	class={cn(
		'w-full min-w-0 resize-none rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40',
		className
	)}
	bind:value
	{...restProps}></textarea>
