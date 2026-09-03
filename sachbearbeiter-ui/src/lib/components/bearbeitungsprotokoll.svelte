<script lang="ts">
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import { SvelteSet } from 'svelte/reactivity';
	import * as Table from '$lib/components/ui/table';
	import { Badge } from '$lib/components/ui/badge';
	import StatusBadge from '$lib/components/status-badge.svelte';
	import { akteurLabel, dateTime } from '$lib/format';
	import type { Protokolleintrag, Protokollakteur, AgentCall } from '$lib/types/api';

	let { entries }: { entries: Protokolleintrag[] } = $props();

	const expanded = new SvelteSet<number>();

	function toggle(index: number) {
		if (expanded.has(index)) expanded.delete(index);
		else expanded.add(index);
	}

	function akteurClass(akteur: Protokollakteur): string {
		if (akteur === 'agent') return 'bg-[var(--akzent)] text-white';
		if (akteur === 'sachbearbeitung') return 'bg-[var(--primary)] text-white';
		return '';
	}

	function akteurVariant(akteur: Protokollakteur): 'secondary' | 'outline' | undefined {
		if (akteur === 'system') return 'secondary';
		if (akteur === 'kunde') return 'outline';
		return undefined;
	}

	function targetStatus(schritt: string): string | null {
		if (!schritt.startsWith('Status ')) return null;
		const parts = schritt.split('-> ');
		return parts.length > 1 ? parts[parts.length - 1].trim() : null;
	}

	function callLine(calls: AgentCall): string {
		const parts = [calls.art, calls.name];
		if (calls.eingabeKurz || calls.ergebnisKurz) {
			parts.push(`${calls.eingabeKurz ?? '—'} → ${calls.ergebnisKurz ?? '—'}`);
		}
		if (calls.dauerMs !== null && calls.dauerMs !== undefined) parts.push(`${calls.dauerMs} ms`);
		return parts.join(' · ');
	}
</script>

<Table.Root>
	<Table.Header>
		<Table.Row>
			<Table.Head class="w-8"></Table.Head>
			<Table.Head>Zeit</Table.Head>
			<Table.Head>Akteur</Table.Head>
			<Table.Head>Schritt</Table.Head>
			<Table.Head>Ergebnis/Detail</Table.Head>
		</Table.Row>
	</Table.Header>
	<Table.Body>
		{#each entries as entry, index (index)}
			{@const target = targetStatus(entry.schritt)}
			{@const hasCalls = (entry.calls?.length ?? 0) > 0}
			<Table.Row>
				<Table.Cell>
					{#if hasCalls}
						<button
							type="button"
							class="text-muted-foreground hover:text-foreground"
							aria-expanded={expanded.has(index)}
							aria-controls="protokoll-calls-{index}"
							aria-label="Agentenaufrufe ein-/ausklappen"
							onclick={() => toggle(index)}
						>
							{#if expanded.has(index)}
								<ChevronDown class="size-4" aria-hidden="true" />
							{:else}
								<ChevronRight class="size-4" aria-hidden="true" />
							{/if}
						</button>
					{/if}
				</Table.Cell>
				<Table.Cell class="whitespace-nowrap">{dateTime(entry.zeitpunkt)}</Table.Cell>
				<Table.Cell>
					<Badge variant={akteurVariant(entry.akteur)} class={akteurClass(entry.akteur)}>
						{akteurLabel(entry.akteur)}
					</Badge>
				</Table.Cell>
				<Table.Cell>
					<div class="flex flex-wrap items-center gap-2">
						<span>{entry.schritt}</span>
						{#if target}<StatusBadge status={target} />{/if}
					</div>
				</Table.Cell>
				<Table.Cell class="text-muted-foreground">{entry.detail ?? '—'}</Table.Cell>
			</Table.Row>
			{#if hasCalls && expanded.has(index)}
				<Table.Row id="protokoll-calls-{index}">
					<Table.Cell></Table.Cell>
					<Table.Cell colspan={4} class="align-top whitespace-normal">
						<ul class="space-y-1 font-mono text-xs text-muted-foreground">
							{#each entry.calls ?? [] as calls, callIndex (callIndex)}
								<li>{callLine(calls)}</li>
							{/each}
						</ul>
					</Table.Cell>
				</Table.Row>
			{/if}
		{/each}
	</Table.Body>
</Table.Root>
