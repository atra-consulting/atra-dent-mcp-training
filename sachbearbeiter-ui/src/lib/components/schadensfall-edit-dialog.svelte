<script lang="ts">
	import { enhance } from '$app/forms';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Button } from '$lib/components/ui/button';
	import { Textarea } from '$lib/components/ui/textarea';
	import * as Alert from '$lib/components/ui/alert';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { ablehnungsgrund, euro, schadensfallstatus } from '$lib/format';
	import type {
		ErstattungsberechnungResult,
		Rechenschritt,
		Schadensfall,
		Schadensfallstatus
	} from '$lib/types/api';

	let {
		schadensfall,
		onClose,
		error,
		calculation
	}: {
		schadensfall: Schadensfall | null;
		onClose: () => void;
		error?: string;
		calculation?: ErstattungsberechnungResult | null;
	} = $props();

	let status = $state<string>('');
	let erstattungsbetrag = $state<string>('');
	$effect(() => {
		status = schadensfall?.status ?? '';
		erstattungsbetrag = schadensfall?.erstattungsbetrag ?? '';
	});

	const hasUnmappedPositionen = $derived(
		schadensfall?.positionen.some((p) => p.leistungsbereich === null) ?? false
	);

	const statusValues: Schadensfallstatus[] = [
		'eingereicht',
		'in_pruefung',
		'genehmigt',
		'abgelehnt',
		'ausgezahlt'
	];
	const ablehnungsgruende = [
		'ANGERATEN',
		'KOSMETIK',
		'NICHT_APPROBIERT',
		'FEHLENDE_ZAEHNE',
		'NICHT_VERSICHERT',
		'WARTEZEIT',
		'SONSTIGES'
	];

	const rechenschrittLabels: Record<Rechenschritt, string> = {
		ERSTATTUNGSFAEHIG: 'Erstattungsfähiger Betrag',
		QUOTE: 'Quote',
		SUBLIMIT: 'Sublimit',
		GKV: 'GKV-Vorleistung',
		SELBSTBEHALT: 'Selbstbehalt',
		STAFFEL: 'Staffel & Jahresgrenze'
	};
</script>

<Dialog.Root open={schadensfall !== null} onOpenChange={(open: boolean) => !open && onClose()}>
	<Dialog.Content class="max-h-[90vh] overflow-y-auto sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>Schadensfall {schadensfall?.id} bearbeiten</Dialog.Title>
		</Dialog.Header>
		{#if error}
			<Alert.Root variant="destructive">
				<Alert.Title>Aktion fehlgeschlagen</Alert.Title>
				<Alert.Description>{error}</Alert.Description>
			</Alert.Root>
		{/if}
		{#if schadensfall}
			<form method="POST" action="?/schadensfallAktualisieren" use:enhance class="grid gap-4">
				<input type="hidden" name="schadensfallId" value={schadensfall.id} />
				<div class="grid gap-1.5">
					<Label for="status">Status</Label>
					<select
						id="status"
						name="status"
						bind:value={status}
						class="h-9 rounded-md border border-input bg-background px-3 text-sm"
					>
						{#each statusValues as value (value)}
							<option {value}>{schadensfallstatus(value)}</option>
						{/each}
					</select>
				</div>
				<div class="grid gap-1.5">
					<Label for="erstattungsbetrag">Erstattungsbetrag (z. B. 450 oder 450,50)</Label>
					<Input id="erstattungsbetrag" name="erstattungsbetrag" bind:value={erstattungsbetrag} />
				</div>
				{#if status === 'abgelehnt'}
					<div class="grid gap-1.5">
						<Label for="ablehnungsgrund">Ablehnungsgrund</Label>
						<select
							id="ablehnungsgrund"
							name="ablehnungsgrund"
							required
							class="h-9 rounded-md border border-input bg-background px-3 text-sm"
						>
							<option value="" disabled selected={!schadensfall.ablehnungsgrund}
								>bitte wählen …</option
							>
							{#each ablehnungsgruende as grund (grund)}
								<option value={grund} selected={schadensfall.ablehnungsgrund === grund}
									>{ablehnungsgrund(grund)}</option
								>
							{/each}
						</select>
					</div>
					<div class="grid gap-1.5">
						<Label for="ablehnungshinweis">Hinweis (Freitext)</Label>
						<Textarea
							id="ablehnungshinweis"
							name="ablehnungshinweis"
							value={schadensfall.ablehnungshinweis ?? ''}
						/>
					</div>
				{/if}

				<div class="rounded-md border border-primary/20 bg-primary/5 p-3">
					<p class="text-sm font-medium">Erstattungsvorschlag des Kernsystems</p>
					<p class="mt-1 text-xs text-muted-foreground">
						Vorverbrauch wird aus der Schadenshistorie abgeleitet; der Selbstbehalt-Verbrauch ist
						daraus nicht bestimmbar und wird mit 0 angesetzt.
					</p>
					<div class="mt-3 flex flex-wrap items-end gap-3">
						<div class="grid gap-1.5">
							<Label for="gkvLeistung">GKV-Vorleistung (z. B. 600)</Label>
							<Input id="gkvLeistung" name="gkvLeistung" class="w-36" placeholder="0" />
						</div>
						<div class="flex items-center gap-2 pb-2">
							<Checkbox id="unfallbedingt" name="unfallbedingt" />
							<Label for="unfallbedingt">unfallbedingt</Label>
						</div>
						<Button
							type="submit"
							formaction="?/erstattungBerechnen"
							variant="secondary"
							size="sm"
							disabled={hasUnmappedPositionen}
						>
							Erstattung berechnen
						</Button>
					</div>
					{#if hasUnmappedPositionen}
						<p class="mt-2 text-xs text-muted-foreground">
							Erstattung kann erst berechnet werden, wenn alle Positionen einem Leistungsbereich
							zugeordnet sind.
						</p>
					{/if}

					{#if calculation}
						<div class="mt-3 border-t border-primary/20 pt-3">
							<ol class="space-y-1 text-xs">
								{#each calculation.schritte as schritt (schritt.schritt)}
									<li class="flex items-baseline justify-between gap-3">
										<span>
											<span class="font-medium">{rechenschrittLabels[schritt.schritt]}</span>
											{#if schritt.erlaeuterung}
												<span class="text-muted-foreground"> — {schritt.erlaeuterung}</span>
											{/if}
										</span>
										<span class="shrink-0 tabular-nums">{euro(schritt.betrag)}</span>
									</li>
								{/each}
							</ol>
							<div class="mt-3 flex items-center justify-between gap-3">
								<p class="text-sm">
									<span class="font-semibold text-primary"
										>{euro(calculation.erstattungsbetrag)}</span
									>
									<span class="text-xs text-muted-foreground">
										· Eigenanteil {euro(calculation.eigenanteil)} · Jahr {calculation.versicherungsjahr}
									</span>
								</p>
								<Button
									type="button"
									size="sm"
									onclick={() => (erstattungsbetrag = calculation.erstattungsbetrag)}
								>
									Betrag übernehmen
								</Button>
							</div>
						</div>
					{/if}
				</div>

				<div class="flex justify-end gap-3">
					<Button type="button" variant="outline" onclick={onClose}>Abbrechen</Button>
					<Button type="submit">Speichern</Button>
				</div>
			</form>
		{/if}
	</Dialog.Content>
</Dialog.Root>
