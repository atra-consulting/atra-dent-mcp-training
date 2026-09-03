<script lang="ts">
	import Brain from '@lucide/svelte/icons/brain';
	import Wrench from '@lucide/svelte/icons/wrench';
	import ArrowLeftRight from '@lucide/svelte/icons/arrow-left-right';
	import { Badge } from '$lib/components/ui/badge';
	import PageHeader from '$lib/components/page-header.svelte';
	import { akteurLabel, dateTime } from '$lib/format';
	import { cn } from '$lib/utils';
	import type { Protokollakteur, AgentCall } from '$lib/types/api';

	let { data } = $props();
	const schadensfall = $derived(data.schadensfall);

	function akteurBadgeClass(akteur: Protokollakteur): string {
		if (akteur === 'agent') return 'bg-[var(--akzent)] text-white';
		if (akteur === 'sachbearbeitung') return 'bg-[var(--primary)] text-white';
		return '';
	}

	function akteurBadgeVariant(akteur: Protokollakteur): 'secondary' | 'outline' | undefined {
		if (akteur === 'system') return 'secondary';
		if (akteur === 'kunde') return 'outline';
		return undefined;
	}

	function akteurBorderClass(akteur: Protokollakteur): string {
		if (akteur === 'agent') return 'border-[var(--akzent)]';
		if (akteur === 'sachbearbeitung') return 'border-[var(--primary)]';
		return 'border-muted-foreground/40';
	}

	function callIcon(art: AgentCall['art']) {
		if (art === 'modell') return Brain;
		if (art === 'tool') return Wrench;
		return ArrowLeftRight;
	}

	function callLine(calls: AgentCall): string {
		const parts: string[] = [];
		if (calls.eingabeKurz || calls.ergebnisKurz) {
			parts.push(`${calls.eingabeKurz ?? '—'} → ${calls.ergebnisKurz ?? '—'}`);
		}
		if (calls.dauerMs !== null && calls.dauerMs !== undefined) parts.push(`${calls.dauerMs} ms`);
		return parts.join(' · ');
	}
</script>

<PageHeader
	eyebrow="Fall {schadensfall.id}"
	eyebrowHref="/faelle/{schadensfall.id}"
	title="Bearbeitungsprotokoll"
/>

{#if schadensfall.bearbeitungsprotokoll.length === 0}
	<p class="text-sm text-muted-foreground">Noch keine Bearbeitung.</p>
{:else}
	<ol class="space-y-6">
		{#each schadensfall.bearbeitungsprotokoll as entry, index (index)}
			<li class={cn('border-l-4 py-0.5 pl-4', akteurBorderClass(entry.akteur))}>
				<div class="flex flex-wrap items-center gap-2">
					<span class="text-sm text-muted-foreground">{dateTime(entry.zeitpunkt)}</span>
					<Badge variant={akteurBadgeVariant(entry.akteur)} class={akteurBadgeClass(entry.akteur)}>
						{akteurLabel(entry.akteur)}
					</Badge>
				</div>
				<p class="mt-1 text-sm font-medium">{entry.schritt}</p>
				{#if entry.detail}
					<p class="mt-0.5 text-sm text-muted-foreground">{entry.detail}</p>
				{/if}
				{#if entry.calls && entry.calls.length > 0}
					<ul class="mt-3 space-y-2 border-l border-dashed border-muted-foreground/30 pl-4">
						{#each entry.calls as calls, callIndex (callIndex)}
							{@const Icon = callIcon(calls.art)}
							{@const detail = callLine(calls)}
							<li class="flex items-start gap-2 text-xs text-muted-foreground">
								<Icon class="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
								<span class="sr-only">{calls.art}</span>
								<span class="font-mono">
									<span class="font-semibold text-foreground">{calls.name}</span>
									{#if detail}
										· {detail}
									{/if}
								</span>
							</li>
						{/each}
					</ul>
				{/if}
			</li>
		{/each}
	</ol>
{/if}
