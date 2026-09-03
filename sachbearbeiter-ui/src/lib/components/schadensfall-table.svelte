<script lang="ts">
	import { resolve } from '$app/paths';
	import * as Table from '$lib/components/ui/table';
	import { Button } from '$lib/components/ui/button';
	import * as Alert from '$lib/components/ui/alert';
	import StatusBadge from '$lib/components/status-badge.svelte';
	import { date, euro } from '$lib/format';
	import type { Schadensfall } from '$lib/types/api';

	let {
		schadensfaelle,
		onEdit
	}: { schadensfaelle: Schadensfall[]; onEdit?: (schadensfall: Schadensfall) => void } = $props();

	let expandedId = $state<number | null>(null);

	function leistungsbereiche(schadensfall: Schadensfall): string {
		return [...new Set(schadensfall.positionen.map((p) => p.leistungsbereich ?? '—'))].join(', ');
	}
</script>

{#if schadensfaelle.length === 0}
	<p class="text-sm text-muted-foreground">Noch keine Schadensfälle vorhanden.</p>
{:else}
	<Table.Root>
		<Table.Header>
			<Table.Row>
				<Table.Head class="w-8"></Table.Head>
				<Table.Head>Fall</Table.Head>
				<Table.Head>Behandlungsdatum</Table.Head>
				<Table.Head>Leistungsbereiche</Table.Head>
				<Table.Head class="text-right">Rechnungsbetrag</Table.Head>
				<Table.Head class="text-right">Erstattung</Table.Head>
				<Table.Head>Status</Table.Head>
			</Table.Row>
		</Table.Header>
		<Table.Body>
			{#each schadensfaelle as schadensfall (schadensfall.id)}
				<Table.Row
					class="cursor-pointer"
					onclick={() => (expandedId = expandedId === schadensfall.id ? null : schadensfall.id)}
				>
					<Table.Cell>{expandedId === schadensfall.id ? '▾' : '▸'}</Table.Cell>
					<Table.Cell>
						<a
							href={resolve('/faelle/[id]', { id: String(schadensfall.id) })}
							class="hover:underline"
							onclick={(e: MouseEvent) => e.stopPropagation()}
						>
							{schadensfall.id}
						</a>
					</Table.Cell>
					<Table.Cell>{date(schadensfall.behandlungsdatum)}</Table.Cell>
					<Table.Cell>{leistungsbereiche(schadensfall)}</Table.Cell>
					<Table.Cell class="text-right">{euro(schadensfall.rechnungsbetrag)}</Table.Cell>
					<Table.Cell class="text-right">{euro(schadensfall.erstattungsbetrag)}</Table.Cell>
					<Table.Cell><StatusBadge status={schadensfall.status} /></Table.Cell>
				</Table.Row>
				{#if expandedId === schadensfall.id}
					<Table.Row>
						<Table.Cell colspan={7} class="bg-muted/50">
							<div class="space-y-3 p-2">
								<table class="w-full text-sm">
									<thead>
										<tr class="text-left text-muted-foreground">
											<th class="py-1 pr-4 font-normal">GOZ</th>
											<th class="py-1 pr-4 font-normal">Zahn</th>
											<th class="py-1 pr-4 font-normal">Bereich</th>
											<th class="py-1 pr-4 font-normal">Beschreibung</th>
											<th class="py-1 text-right font-normal">Betrag</th>
										</tr>
									</thead>
									<tbody>
										{#each schadensfall.positionen as position, i (i)}
											<tr>
												<td class="py-1 pr-4">
													{#if position.goz}
														{position.goz}
													{:else}
														<span class="text-muted-foreground">ohne Ziffer</span>
													{/if}
												</td>
												<td class="py-1 pr-4">{position.zahn ?? '—'}</td>
												<td class="py-1 pr-4">{position.leistungsbereich ?? '—'}</td>
												<td class="py-1 pr-4">{position.beschreibung}</td>
												<td class="py-1 text-right">{euro(position.betrag)}</td>
											</tr>
										{/each}
									</tbody>
								</table>
								{#if schadensfall.status === 'abgelehnt' && schadensfall.ablehnungsgrund}
									<Alert.Root variant="destructive">
										<Alert.Title>Abgelehnt: {schadensfall.ablehnungsgrund}</Alert.Title>
										{#if schadensfall.ablehnungshinweis}
											<Alert.Description>{schadensfall.ablehnungshinweis}</Alert.Description>
										{/if}
									</Alert.Root>
								{/if}
								<div class="flex items-center gap-2">
									{#if onEdit}
										<Button
											variant="outline"
											size="sm"
											onclick={(e: MouseEvent) => {
												e.stopPropagation();
												onEdit(schadensfall);
											}}
										>
											Bearbeiten
										</Button>
									{/if}
								</div>
							</div>
						</Table.Cell>
					</Table.Row>
				{/if}
			{/each}
		</Table.Body>
	</Table.Root>
{/if}
