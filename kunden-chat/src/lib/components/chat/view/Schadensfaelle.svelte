<script lang="ts">
	import { date, currency } from '$lib/format';
	import {
		ablehnungsgrundLabel,
		STAGE_LABELS,
		statusNote,
		statusStage,
		type SchadensfaelleView
	} from '$lib/types/view';

	let { view }: { view: SchadensfaelleView } = $props();

	const schadensfaelle = $derived(
		[...(view.faelle ?? [])].sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
	);

	function rule(stage: number, current: number, rejected: boolean): string {
		if (stage > current) return 'border-border text-muted-foreground/60';
		if (rejected) {
			return stage === current
				? 'border-destructive font-medium text-destructive'
				: 'border-destructive/40 text-muted-foreground';
		}
		return stage === current
			? 'border-primary font-medium text-foreground'
			: 'border-primary/40 text-muted-foreground';
	}
</script>

{#if schadensfaelle.length > 0}
	<div class="divide-y divide-border">
		{#each schadensfaelle as schadensfall, index (schadensfall.id ?? index)}
			{@const current = statusStage(schadensfall.status ?? undefined)}
			{@const note = statusNote(schadensfall.status ?? undefined)}
			{@const erstattungsbetrag = currency(schadensfall.erstattungsbetrag)}
			{@const erstattungsvorschlag = currency(schadensfall.erstattungsvorschlag)}
			{@const ablehnungsgrund = ablehnungsgrundLabel(schadensfall.ablehnungsgrund)}
			<div class="p-3">
				<div class="mb-3 flex items-baseline justify-between gap-2">
					<h3 class="text-sm font-medium">
						{schadensfall.id !== undefined ? `Fall Nr. ${schadensfall.id}` : 'Fall'}
					</h3>
					{#if date(schadensfall.behandlungsdatum)}
						<span class="text-xs text-muted-foreground">
							Behandlung am {date(schadensfall.behandlungsdatum)}
						</span>
					{/if}
				</div>

				<ol class="flex gap-1 text-xs" aria-label="Bearbeitungsstand">
					{#each STAGE_LABELS as label, stage (label)}
						<li
							class="flex-1 border-t-2 pt-1.5 leading-tight {rule(
								stage,
								current.stage,
								current.rejected
							)}"
							aria-current={stage === current.stage ? 'step' : undefined}
						>
							{label}
						</li>
					{/each}
				</ol>

				{#if note}
					<p class="mt-2.5 text-sm {current.rejected ? 'text-destructive' : 'text-foreground'}">
						<span class="font-medium">{note}</span>
						{#if current.rejected && ablehnungsgrund !== 'Abgelehnt'}
							<span class="block text-xs">{ablehnungsgrund}</span>
						{/if}
						{#if current.rejected && schadensfall.ablehnungshinweis}
							<span class="block text-xs text-destructive/80">
								{schadensfall.ablehnungshinweis}
							</span>
						{/if}
					</p>
				{/if}

				<dl class="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
					<dt class="text-muted-foreground">Rechnungsbetrag</dt>
					<dd class="tabular-nums">{currency(schadensfall.rechnungsbetrag) ?? '—'}</dd>

					{#if erstattungsbetrag}
						<dt class="text-muted-foreground">Erstattungsbetrag</dt>
						<dd class="tabular-nums">{erstattungsbetrag}</dd>
					{:else if erstattungsvorschlag}
						<dt class="text-muted-foreground">Erstattungsvorschlag</dt>
						<dd class="tabular-nums">
							{erstattungsvorschlag}
							<span class="text-xs text-muted-foreground">keine Zusage</span>
						</dd>
					{:else}
						<dt class="text-muted-foreground">Erstattung</dt>
						<dd class="text-muted-foreground">noch offen</dd>
					{/if}
				</dl>
			</div>
		{/each}
	</div>
{/if}
