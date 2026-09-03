<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import PageHeader from '$lib/components/page-header.svelte';
	import SortableColumn from '$lib/components/sortable-column.svelte';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { date, euro } from '$lib/format';
	import Search from '@lucide/svelte/icons/search';
	import UserPlus from '@lucide/svelte/icons/user-plus';
	import X from '@lucide/svelte/icons/x';
	let { data } = $props();

	type Customer = (typeof data.kunden)[number];

	let filter = $state('');
	let tarifFilter = $state<string | null>(null);
	let sortColumn = $state('id');
	let sortDirection = $state<'asc' | 'desc'>('asc');

	const collator = new Intl.Collator('de', { numeric: true, sensitivity: 'base' });

	const tarifOrder = ['ATRA_DENT_S', 'ATRA_DENT_B', 'ATRA_DENT_X', 'ATRA_DENT_X_SB'];

	const tarifColors: Record<string, string> = {
		ATRA_DENT_S: 'var(--secondary)',
		ATRA_DENT_B: 'color-mix(in srgb, var(--secondary) 45%, var(--primary))',
		ATRA_DENT_X: 'var(--akzent-hell)',
		ATRA_DENT_X_SB: 'var(--akzent)'
	};

	const columnLabels: Record<string, string> = {
		id: 'Nr.',
		name: 'Name',
		geburtsdatum: 'Geburtsdatum',
		ort: 'Ort',
		tarif: 'Tarif',
		status: 'Status',
		balance: 'Wirtschaftlichkeit'
	};

	const tarifGroups = $derived(
		tarifOrder
			.map((id) => {
				const members = data.kunden.filter((c) => c.tarifId === id);
				return { id, name: members[0]?.tarifName ?? id, count: members.length };
			})
			.filter((group) => group.count > 0)
	);

	const activeCount = $derived(data.kunden.filter((c) => c.status !== 'inaktiv').length);

	const filtered = $derived(
		data.kunden.filter((c) => {
			if (tarifFilter && c.tarifId !== tarifFilter) return false;
			const searchText =
				`${c.vorname} ${c.nachname} ${c.id} ${c.email} ${c.adresse.ort} ${c.tarifName}`.toLowerCase();
			return searchText.includes(filter.toLowerCase().trim());
		})
	);

	function compare(a: Customer, b: Customer): number {
		switch (sortColumn) {
			case 'name':
				return collator.compare(`${a.nachname} ${a.vorname}`, `${b.nachname} ${b.vorname}`);
			case 'geburtsdatum':
				return a.geburtsdatum.localeCompare(b.geburtsdatum);
			case 'ort':
				return collator.compare(a.adresse.ort, b.adresse.ort);
			case 'tarif':
				return tarifOrder.indexOf(a.tarifId) - tarifOrder.indexOf(b.tarifId);
			case 'status':
				return collator.compare(a.status ?? '', b.status ?? '');
			case 'balance':
				return (a.balance ?? 0) - (b.balance ?? 0);
			default:
				return a.id - b.id;
		}
	}

	const sorted = $derived.by(() => {
		const rows = [...filtered];
		rows.sort((a, b) => {
			if (sortColumn === 'balance' && (a.balance === null) !== (b.balance === null)) {
				return a.balance === null ? 1 : -1;
			}
			const result = sortDirection === 'asc' ? compare(a, b) : -compare(a, b);
			return result || a.id - b.id;
		});
		return rows;
	});

	const customSorted = $derived(sortColumn !== 'id' || sortDirection !== 'asc');
	const summary = $derived(
		filtered.length === data.kunden.length
			? `${data.kunden.length} Akten · ${activeCount} aktiv · ${data.kunden.length - activeCount} stillgelegt`
			: `${filtered.length} von ${data.kunden.length} Akten`
	);

	function sortBy(column: string) {
		if (sortColumn === column) {
			sortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
		} else {
			sortColumn = column;
			sortDirection = 'asc';
		}
	}

	function resetSort() {
		sortColumn = 'id';
		sortDirection = 'asc';
	}

	function openKunde(event: MouseEvent, id: number) {
		if ((event.target as Element).closest('a, button')) return;
		location.href = `/kunden/${id}`;
	}

	function resetFilters() {
		filter = '';
		tarifFilter = null;
	}
</script>

<PageHeader eyebrow="Bestand" title="Kunden" subline={summary}>
	{#snippet actions()}
		<Button href="/kunden/neu" variant="secondary">
			<UserPlus aria-hidden="true" />
			Kunde anlegen
		</Button>
	{/snippet}

	<div class="mt-5">
		<div class="flex h-2.5 gap-0.5 overflow-hidden rounded-full" aria-hidden="true">
			{#each tarifGroups as group (group.id)}
				<div
					class="transition-opacity {tarifFilter && tarifFilter !== group.id ? 'opacity-25' : ''}"
					style="flex: {group.count}; background-color: {tarifColors[group.id]}"
				></div>
			{/each}
		</div>

		<div class="mt-3 flex flex-wrap gap-2">
			{#each tarifGroups as group (group.id)}
				<button
					type="button"
					aria-pressed={tarifFilter === group.id}
					onclick={() => (tarifFilter = tarifFilter === group.id ? null : group.id)}
					class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium transition-colors focus-visible:ring-2 focus-visible:ring-white/50 focus-visible:outline-none
						{tarifFilter === group.id
						? 'border-white/60 bg-white/15 text-white'
						: 'border-white/15 text-white/75 hover:border-white/35 hover:bg-white/10 hover:text-white'}"
				>
					<span
						class="size-2 shrink-0 rounded-full"
						style="background-color: {tarifColors[group.id]}"
					></span>
					{group.name}
					<span class="tabular-nums opacity-60">{group.count}</span>
				</button>
			{/each}
		</div>
	</div>

	<div class="mt-5 flex flex-wrap items-center gap-3">
		<div class="relative w-full max-w-sm">
			<Search
				class="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-white/50"
				aria-hidden="true"
			/>
			<Input
				class="h-9 border-white/20 bg-white/10 pl-8 text-white placeholder:text-white/50 focus-visible:border-white/50 focus-visible:ring-white/20"
				placeholder="Suchen: Name, Kundennummer, Ort …"
				aria-label="Kunden durchsuchen"
				bind:value={filter}
			/>
		</div>

		{#if customSorted}
			<div
				class="inline-flex items-center gap-1.5 rounded-full bg-white/10 py-1 pr-1 pl-3 text-xs text-white/80"
			>
				Sortiert nach {columnLabels[sortColumn]}
				{sortDirection === 'asc' ? 'aufsteigend' : 'absteigend'}
				<button
					type="button"
					onclick={resetSort}
					aria-label="Sortierung zurücksetzen"
					class="rounded-full p-0.5 transition-colors hover:bg-white/20 focus-visible:ring-2 focus-visible:ring-white/50 focus-visible:outline-none"
				>
					<X class="size-3.5" aria-hidden="true" />
				</button>
			</div>
		{/if}
	</div>
</PageHeader>

<Card.Root class="py-0">
	<Table.Root>
		<Table.Header>
			<Table.Row>
				<SortableColumn
					column="id"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Nr.</SortableColumn
				>
				<SortableColumn
					column="name"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Name</SortableColumn
				>
				<SortableColumn
					column="geburtsdatum"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Geburtsdatum</SortableColumn
				>
				<SortableColumn
					column="ort"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Ort</SortableColumn
				>
				<SortableColumn
					column="tarif"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Tarif</SortableColumn
				>
				<SortableColumn
					column="status"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}>Status</SortableColumn
				>
				<SortableColumn
					column="balance"
					activeColumn={sortColumn}
					direction={sortDirection}
					onsort={sortBy}
					align="right">Wirtschaftlichkeit</SortableColumn
				>
			</Table.Row>
		</Table.Header>
		<Table.Body>
			{#each sorted as kunde (kunde.id)}
				<Table.Row class="cursor-pointer" onclick={(event) => openKunde(event, kunde.id)}>
					<Table.Cell class="tabular-nums">{kunde.id}</Table.Cell>
					<Table.Cell class="font-medium">
						<a href="/kunden/{kunde.id}" class="flex items-center gap-2.5 hover:underline">
							<img src={kunde.avatar} alt="" class="size-8 shrink-0 rounded-full" loading="lazy" />
							{kunde.nachname}, {kunde.vorname}
						</a>
					</Table.Cell>
					<Table.Cell>{date(kunde.geburtsdatum)}</Table.Cell>
					<Table.Cell>{kunde.adresse.ort}</Table.Cell>
					<Table.Cell><Badge variant="secondary">{kunde.tarifName}</Badge></Table.Cell>
					<Table.Cell>
						{#if kunde.status === 'inaktiv'}<Badge variant="outline">inaktiv</Badge>{:else}<Badge
								>aktiv</Badge
							>{/if}
					</Table.Cell>
					<Table.Cell class="text-right">
						{#if kunde.balance === null}
							<span class="text-muted-foreground">—</span>
						{:else}
							<span
								class="font-medium tabular-nums {kunde.balance < 0
									? 'text-destructive'
									: 'text-primary'}"
							>
								{kunde.balance > 0 ? '+' : ''}{euro(kunde.balance)}
							</span>
						{/if}
					</Table.Cell>
				</Table.Row>
			{:else}
				<Table.Row>
					<Table.Cell colspan={7} class="py-10 text-center">
						<p class="text-muted-foreground">Keine Akte passt zu dieser Suche.</p>
						<Button variant="outline" size="sm" class="mt-3" onclick={resetFilters}>
							Filter zurücksetzen
						</Button>
					</Table.Cell>
				</Table.Row>
			{/each}
		</Table.Body>
	</Table.Root>
</Card.Root>
