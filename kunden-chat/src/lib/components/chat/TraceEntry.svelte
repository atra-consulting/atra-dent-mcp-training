<script lang="ts">
	import { displayName, duration, offset, panelFields, transport, unpack, withElapsed } from '$lib/trace';
	import type { Protocol, TracePoint } from '$lib/types/chat';

	let {
		trace,
		result,
		failed,
		resultTime,
		level,
		returnHop,
		start
	}: {
		trace: TracePoint;
		result?: unknown;
		failed?: unknown;
		resultTime?: string;
		level: number;
		returnHop: boolean;
		start?: string;
	} = $props();

	const colors: Record<Protocol, string> = {
		A2A: 'bg-primary/10 text-primary ring-primary/25',
		MCP: 'bg-akzent/10 text-akzent ring-akzent/25',
		MODELL: 'bg-secondary/25 text-secondary-foreground ring-secondary/50',
		INTERNAL: 'bg-transparent text-muted-foreground ring-border'
	};

	const nodes: Record<Protocol, string> = {
		A2A: 'bg-primary',
		MCP: 'bg-akzent',
		MODELL: 'bg-secondary',
		INTERNAL: 'bg-border'
	};

	const nodeBorders: Record<Protocol, string> = {
		A2A: 'border-primary',
		MCP: 'border-akzent',
		MODELL: 'border-secondary',
		INTERNAL: 'border-border'
	};

	const nodeArrows: Record<Protocol, string> = {
		A2A: 'border-r-primary',
		MCP: 'border-r-akzent',
		MODELL: 'border-r-secondary',
		INTERNAL: 'border-r-border'
	};

	const nodeShape = $derived(
		returnHop
			? 'return'
			: trace.protocol === 'MODELL'
				? 'decision'
				: trace.protocol === 'INTERNAL'
					? 'internal'
					: 'boundary'
	);

	const time = $derived(offset(trace.timestamp, start));
	const elapsed = $derived(duration(trace.timestamp, resultTime));

	const fields = $derived(panelFields(trace, result, failed));

	const hasData = $derived(fields.length > 0);

	const SHORT = 200;

	const compact = $derived(
		fields.reduce((sum: number, [, value]) => sum + render(value).length, 0) <= SHORT
	);

	const LONG = 400;

	function brief(content: string): string {
		const oneLine = content.replace(/\s+/g, ' ').trim();
		return oneLine.length > 120 ? `${oneLine.slice(0, 120)}…` : oneLine;
	}

	function size(content: string): string {
		const lines = content.split('\n').length;
		const chars = `${content.length.toLocaleString('de-DE')} Zeichen`;
		return lines > 1 ? `${lines} Zeilen, ${chars}` : chars;
	}

	function render(value: unknown): string {
		const unpacked = unpack(value);
		return typeof unpacked === 'string' ? unpacked : JSON.stringify(unpacked, null, 2);
	}
</script>

<div class="flex gap-2 text-xs" style="margin-left: {level * 1.25}rem">
	<div class="relative flex w-2.5 shrink-0 justify-center">
		<span class="absolute inset-y-0 w-px bg-border" aria-hidden="true"></span>
		{#if nodeShape === 'return'}
			<span
				class="absolute top-1 left-1/2 size-3 -translate-x-1/2 rounded-full bg-background"
				aria-hidden="true"
			></span>
			<span
				class="relative mt-1.5 size-0 border-y-4 border-r-4 border-y-transparent {nodeArrows[
					trace.protocol
				]}"
				aria-hidden="true"
			></span>
		{:else if nodeShape === 'decision'}
			<span
				class="relative mt-1.5 size-2 rounded-full border-2 ring-2 ring-background {nodeBorders[
					trace.protocol
				]}"
				aria-hidden="true"
			></span>
		{:else if nodeShape === 'internal'}
			<span
				class="relative mt-2 size-1 rounded-full ring-2 ring-background {nodes[trace.protocol]}"
				aria-hidden="true"
			></span>
		{:else}
			<span
				class="relative mt-1.5 size-2 rounded-full ring-2 ring-background {nodes[trace.protocol]}"
				aria-hidden="true"
			></span>
		{/if}
	</div>

	<div class="min-w-0 flex-1 pb-2">
		<div class="flex flex-wrap items-center gap-x-1.5 gap-y-1">
			<span
				class="rounded px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-wide ring-1 ring-inset {colors[
					trace.protocol
				]}"
			>
				{trace.protocol}
			</span>

			{#if trace.sender}
				<span class="font-medium text-foreground/80">{displayName(trace.sender)}</span>
			{/if}
			{#if trace.peer}
				<span class="text-muted-foreground/50" aria-hidden="true">
					{returnHop ? '◀' : '▶'}
				</span>
				<span class="sr-only">{returnHop ? 'zurück an' : 'an'}</span>
				<span class="font-medium text-foreground/80">{displayName(trace.peer)}</span>
			{/if}

			{#if time}
				<span
					class="ml-auto shrink-0 font-mono text-[10px] text-muted-foreground/70 tabular-nums"
					title="gemeldet um {trace.timestamp}"
				>
					{time}
				</span>
			{/if}
		</div>

		<p class="mt-0.5 text-[13px] font-medium text-foreground">{trace.label ?? trace.text}</p>

		{#if trace.operation}
			<code class="font-mono text-[10px] text-muted-foreground">{trace.operation}</code>
		{/if}

		{#if result !== undefined}
			<p class="mt-1 text-[10px] text-muted-foreground">
				<span aria-hidden="true">◀</span>
				{withElapsed('Erfolgreich', elapsed)}
			</p>
		{:else if failed !== undefined}
			<p class="mt-1 text-[10px] text-destructive">
				<span aria-hidden="true">◀</span>
				Gescheitert · {withElapsed(brief(render(failed)), elapsed)}
			</p>
		{/if}

		{#if hasData}
			<details class="group mt-1" open={compact}>
				<summary
					class="cursor-pointer list-none text-[11px] text-muted-foreground/80 select-none hover:text-muted-foreground"
				>
					<span class="group-open:hidden">▸ Angaben · {fields.length}</span>
					<span class="hidden group-open:inline">▾ Angaben</span>
				</summary>
				<dl class="mt-1 space-y-1">
					{#each fields as [field, value] (field)}
						{@const content = render(value)}
						<div class={transport(field) ? 'border-l-2 border-akzent/60 pl-1.5' : ''}>
							<dt
								class="font-mono text-[10px] {transport(field)
									? 'font-semibold text-akzent'
									: 'text-muted-foreground'}"
							>
								{#if transport(field)}<span
										class="mr-0.5 font-sans"
										title="über den Transport gesendet, nicht vom Modell erzeugt"
										aria-hidden="true">⇥</span
									><span class="sr-only"
										>über den Transport gesendet, nicht vom Modell erzeugt:&nbsp;</span
									>{/if}{field}
							</dt>
							<dd>
								{#if content.length > LONG}
									<details class="group/value">
										<summary
											class="cursor-pointer list-none text-[11px] text-muted-foreground/80 select-none hover:text-muted-foreground"
										>
											<span class="group-open/value:hidden">▸ {size(content)}</span>
											<span class="hidden group-open/value:inline">▾ einklappen</span>
										</summary>
										<pre
											class="mt-1 max-h-48 overflow-auto rounded bg-muted/60 px-2 py-1 font-mono text-[11px] whitespace-pre-wrap text-foreground/80">{content}</pre>
									</details>
								{:else}
									<pre
										class="max-h-48 overflow-auto rounded bg-muted/60 px-2 py-1 font-mono text-[11px] whitespace-pre-wrap text-foreground/80">{content}</pre>
								{/if}
							</dd>
						</div>
					{/each}
				</dl>
			</details>
		{/if}
	</div>
</div>
