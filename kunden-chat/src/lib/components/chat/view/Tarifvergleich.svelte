<script lang="ts">
	import { resolve } from '$app/paths';
	import * as Table from '$lib/components/ui/table';
	import { currency, months, percent } from '$lib/format';
	import type { Leistung, TarifvergleichView } from '$lib/types/view';

	let { view }: { view: TarifvergleichView } = $props();

	const tarife = $derived(view.tarife ?? []);
	const leistungsbereiche = $derived(view.leistungsbereiche ?? []);
	const ownTarif = $derived(view.vertragstarif?.schluessel ?? null);

	const displayName = (schluessel?: string, anzeigename?: string | null) =>
		anzeigename ?? schluessel ?? '—';

	const ownTarifListed = $derived(
		ownTarif !== null && tarife.some((tarif) => tarif.schluessel === ownTarif)
	);

	function cell(leistung: Leistung | undefined): string {
		if (!leistung) return '—';
		if (leistung.versichert === false) return 'nicht versichert';

		const parts: string[] = [];
		const quote = percent(leistung.quote);
		if (quote) parts.push(quote);

		const perYear = currency(leistung.limitProJahr);
		if (perYear) parts.push(`höchstens ${perYear} im Jahr`);

		const total = currency(leistung.limitGesamt);
		if (total) parts.push(`höchstens ${total} insgesamt`);

		if (typeof leistung.maxFaelle === 'number') {
			const period =
				typeof leistung.zeitraumJahre === 'number' ? ` in ${leistung.zeitraumJahre} Jahren` : '';
			parts.push(`bis zu ${leistung.maxFaelle} Fälle${period}`);
		} else if (typeof leistung.faelleProJahr === 'number') {
			parts.push(`${leistung.faelleProJahr}× im Jahr`);
		}

		const wartezeit = months(leistung.wartezeitMonate);
		if (wartezeit) parts.push(`${wartezeit} Wartezeit`);

		if (leistung.bedingung) parts.push(leistung.bedingung);

		if (parts.length === 0) return leistung.versichert === true ? 'versichert' : '—';
		return parts.join(', ');
	}

	const withBedingungswerk = $derived(tarife.some((tarif) => !!tarif.bedingungswerk));

	const baseRows = $derived(
		[
			{
				name: 'Selbstbehalt',
				values: tarife.map((tarif) => currency(tarif.selbstbehalt) ?? '—')
			},
			{
				name: 'Jahreshöchstgrenze',
				values: tarife.map((tarif) => currency(tarif.jahreshoechstgrenze) ?? 'unbegrenzt')
			},
			{
				name: 'Wartezeit',
				values: tarife.map((tarif) => months(tarif.wartezeitMonate) ?? '—')
			}
		].filter((row) => row.values.some((value) => value !== '—'))
	);
</script>

{#if tarife.length > 0}
	<Table.Root>
		<Table.Header>
			<Table.Row>
				<Table.Head class="min-w-36">Leistung</Table.Head>
				{#each tarife as tarif, column (column)}
					<Table.Head
						class="min-w-44 {tarif.schluessel === ownTarif
							? 'bg-accent text-accent-foreground'
							: ''}"
					>
						<span class="block font-medium text-foreground">
							{displayName(tarif.schluessel, tarif.anzeigename)}
						</span>
						{#if tarif.schluessel === ownTarif}
							<span class="block text-xs font-normal text-accent-foreground">Ihr Tarif</span>
						{:else if tarif.positionierung}
							<span class="block text-xs font-normal text-muted-foreground">
								{tarif.positionierung}
							</span>
						{/if}
					</Table.Head>
				{/each}
			</Table.Row>
		</Table.Header>
		<Table.Body>
			{#each baseRows as row (row.name)}
				<Table.Row>
					<Table.Cell class="font-medium">{row.name}</Table.Cell>
					{#each row.values as value, column (column)}
						<Table.Cell class={tarife[column]?.schluessel === ownTarif ? 'bg-accent' : ''}>
							{value}
						</Table.Cell>
					{/each}
				</Table.Row>
			{/each}
			{#each leistungsbereiche as leistungsbereich, index (index)}
				<Table.Row>
					<Table.Cell class="font-medium">
						{leistungsbereich.name ?? leistungsbereich.schluessel ?? '—'}
					</Table.Cell>
					{#each tarife as tarif, column (column)}
						<Table.Cell class={tarif.schluessel === ownTarif ? 'bg-accent' : ''}>
							{cell(tarif.schluessel ? leistungsbereich.leistungen?.[tarif.schluessel] : undefined)}
						</Table.Cell>
					{/each}
				</Table.Row>
			{/each}
			{#if withBedingungswerk}
				<Table.Row>
					<Table.Cell class="font-medium">Zum Nachlesen</Table.Cell>
					{#each tarife as tarif, column (column)}
						<Table.Cell class={tarif.schluessel === ownTarif ? 'bg-accent' : ''}>
							{#if tarif.bedingungswerk}
								<a
									class="underline underline-offset-2 hover:text-primary"
									href={resolve('/wissen/dokumente/[dokumentId]/pdf', {
										dokumentId: tarif.bedingungswerk
									})}
									target="_blank"
									rel="noopener"
								>
									Bedingungswerk
								</a>
							{:else}
								—
							{/if}
						</Table.Cell>
					{/each}
				</Table.Row>
			{/if}
		</Table.Body>
		<Table.Caption class="px-3 pb-3">
			{#if ownTarif && ownTarifListed}
				Hervorgehoben ist
				{displayName(ownTarif, view.vertragstarif?.anzeigename)} — Ihr Tarif.
			{:else if ownTarif}
				Ihr Tarif {displayName(ownTarif, view.vertragstarif?.anzeigename)} steht hier nicht daneben.
			{/if}
			{#if withBedingungswerk}
				Die Werte kommen aus dem Produktmodell; verbindlich ist das Bedingungswerk.
			{/if}
			{#if view.stand}
				Stand des Produktmodells: {view.stand}.
			{/if}
		</Table.Caption>
	</Table.Root>
{/if}
