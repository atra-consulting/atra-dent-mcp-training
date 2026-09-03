<script lang="ts">
	import * as Table from '$lib/components/ui/table';
	import * as Card from '$lib/components/ui/card';
	import PageHeader from '$lib/components/page-header.svelte';
	import StatusBadge from '$lib/components/status-badge.svelte';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { Label } from '$lib/components/ui/label';
	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onDestroy, onMount } from 'svelte';
	import { date, dateTime, euro } from '$lib/format';
	import type { Schadensfallstatus } from '$lib/types/api';

	let { data } = $props();

	let auto = $state(true);
	let timer: ReturnType<typeof setInterval> | undefined;

	onMount(() => {
		timer = setInterval(() => {
			if (auto) invalidateAll();
		}, 5000);
	});
	onDestroy(() => {
		if (timer) clearInterval(timer);
	});

	function isSelected(status: Schadensfallstatus): boolean {
		return data.filter.includes(status);
	}

	function toggleStatus(status: Schadensfallstatus, checked: boolean) {
		const next = checked ? [...data.filter, status] : data.filter.filter((s) => s !== status);
		const query = next.join(',');
		goto(resolve(`/faelle?status=${query}`), { keepFocus: true, noScroll: true });
	}

	function openSchadensfall(event: MouseEvent, id: number) {
		if ((event.target as Element).closest('a, button')) return;
		location.href = `/faelle/${id}`;
	}
</script>

<PageHeader
	variant="slim"
	eyebrow="Arbeitsvorrat"
	title="Fälle"
	subline="Schadensfälle über alle Kunden, gefiltert nach Status; aktualisiert sich automatisch."
/>

<Card.Root>
	<Card.Header>
		<Card.Title class="text-base">Filter</Card.Title>
	</Card.Header>
	<Card.Content class="flex flex-wrap items-center gap-x-6 gap-y-3">
		{#each data.statusOptions as status (status)}
			<div class="flex items-center gap-2">
				<Checkbox
					id="status-{status}"
					checked={isSelected(status)}
					onCheckedChange={(checked: boolean) => toggleStatus(status, checked)}
				/>
				<Label for="status-{status}" class="font-normal"><StatusBadge {status} /></Label>
			</div>
		{/each}
		<div class="ml-auto flex items-center gap-2 border-l border-border pl-6">
			<Checkbox id="auto-refresh" bind:checked={auto} />
			<Label for="auto-refresh" class="font-normal">Automatisch aktualisieren</Label>
		</div>
	</Card.Content>
</Card.Root>

<Card.Root>
	<Card.Header>
		<Card.Title class="text-base">
			{data.schadensfaelle.length === 1 ? 'Ein Fall' : `${data.schadensfaelle.length} Fälle`}
		</Card.Title>
	</Card.Header>
	<Card.Content>
		{#if data.schadensfaelle.length === 0}
			<p class="text-sm text-muted-foreground">Keine Fälle für die gewählten Stati.</p>
		{:else}
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>Fall</Table.Head>
						<Table.Head>Kunde</Table.Head>
						<Table.Head>Eingereicht</Table.Head>
						<Table.Head>Behandlung</Table.Head>
						<Table.Head class="text-right">Rechnungsbetrag</Table.Head>
						<Table.Head>Empfehlung</Table.Head>
						<Table.Head>Status</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.schadensfaelle as schadensfall (schadensfall.id)}
						<Table.Row
							class="cursor-pointer"
							onclick={(event) => openSchadensfall(event, schadensfall.id)}
						>
							<Table.Cell>
								<a
									href={resolve('/faelle/[id]', { id: String(schadensfall.id) })}
									class="hover:underline"
								>
									{schadensfall.id}
								</a>
							</Table.Cell>
							<Table.Cell class="font-medium">
								<a href="/kunden/{schadensfall.kundenId}" class="hover:underline"
									>{schadensfall.kundenName}</a
								>
							</Table.Cell>
							<Table.Cell>{dateTime(schadensfall.eingereichtAm)}</Table.Cell>
							<Table.Cell>{date(schadensfall.behandlungsdatum)}</Table.Cell>
							<Table.Cell class="text-right">{euro(schadensfall.rechnungsbetrag)}</Table.Cell>
							<Table.Cell>
								{#if schadensfall.bewertung}
									<div>
										{schadensfall.bewertung.empfehlung === 'freigabe' ? 'Freigabe' : 'Eskalation'}
									</div>
									{#if schadensfall.bewertung.erstattungsvorschlag}
										<div class="text-xs text-muted-foreground">
											{euro(schadensfall.bewertung.erstattungsvorschlag)}
										</div>
									{/if}
								{:else}
									—
								{/if}
							</Table.Cell>
							<Table.Cell><StatusBadge status={schadensfall.status} /></Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		{/if}
	</Card.Content>
</Card.Root>
