<script lang="ts">
	import { enhance } from '$app/forms';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as Alert from '$lib/components/ui/alert';
	import type { Kunde } from '$lib/types/api';

	let {
		kunde,
		tarife,
		error
	}: { kunde?: Kunde; tarife: { id: string; name: string }[]; error?: string } = $props();
</script>

{#if error}
	<Alert.Root variant="destructive" class="mb-4">
		<Alert.Title>Speichern fehlgeschlagen</Alert.Title>
		<Alert.Description>{error}</Alert.Description>
	</Alert.Root>
{/if}

<form method="POST" use:enhance class="grid gap-4">
	<div class="grid grid-cols-2 gap-4">
		<div class="grid gap-1.5">
			<Label for="vorname">Vorname</Label>
			<Input id="vorname" name="vorname" required value={kunde?.vorname ?? ''} />
		</div>
		<div class="grid gap-1.5">
			<Label for="nachname">Nachname</Label>
			<Input id="nachname" name="nachname" required value={kunde?.nachname ?? ''} />
		</div>
	</div>
	<div class="grid gap-1.5">
		<Label for="geburtsdatum">Geburtsdatum</Label>
		<Input
			id="geburtsdatum"
			name="geburtsdatum"
			type="date"
			required
			value={kunde?.geburtsdatum ?? ''}
		/>
	</div>
	<div class="grid grid-cols-2 gap-4">
		<div class="grid gap-1.5">
			<Label for="email">E-Mail</Label>
			<Input id="email" name="email" type="email" required value={kunde?.email ?? ''} />
		</div>
		<div class="grid gap-1.5">
			<Label for="telefon">Telefon</Label>
			<Input id="telefon" name="telefon" required value={kunde?.telefon ?? ''} />
		</div>
	</div>
	<div class="grid grid-cols-[2fr_1fr] gap-4">
		<div class="grid gap-1.5">
			<Label for="strasse">Strasse</Label>
			<Input id="strasse" name="strasse" required value={kunde?.adresse.strasse ?? ''} />
		</div>
		<div class="grid gap-1.5">
			<Label for="plz">PLZ</Label>
			<Input id="plz" name="plz" required value={kunde?.adresse.plz ?? ''} />
		</div>
	</div>
	<div class="grid grid-cols-[2fr_1fr] gap-4">
		<div class="grid gap-1.5">
			<Label for="ort">Ort</Label>
			<Input id="ort" name="ort" required value={kunde?.adresse.ort ?? ''} />
		</div>
		<div class="grid gap-1.5">
			<Label for="land">Land</Label>
			<Input id="land" name="land" required value={kunde?.adresse.land ?? 'DE'} />
		</div>
	</div>
	<div class="grid grid-cols-2 gap-4">
		<div class="grid gap-1.5">
			<Label for="tarifId">Tarif</Label>
			<select
				id="tarifId"
				name="tarifId"
				required
				class="h-9 rounded-md border border-input bg-background px-3 text-sm"
			>
				{#each tarife as tarif (tarif.id)}
					<option value={tarif.id} selected={kunde?.tarifId === tarif.id}>{tarif.name}</option>
				{/each}
			</select>
		</div>
		<div class="grid gap-1.5">
			<Label for="versicherungsbeginn">Versicherungsbeginn</Label>
			<Input
				id="versicherungsbeginn"
				name="versicherungsbeginn"
				type="date"
				required
				value={kunde?.versicherungsbeginn ?? ''}
			/>
		</div>
	</div>
	<div class="grid grid-cols-2 gap-4">
		<div class="grid gap-1.5">
			<Label for="fehlendeZaehne">Fehlende Zähne (0–32)</Label>
			<Input
				id="fehlendeZaehne"
				name="fehlendeZaehne"
				type="number"
				min="0"
				max="32"
				required
				value={String(kunde?.fehlendeZaehne ?? 0)}
			/>
		</div>
		<div class="flex items-end gap-2 pb-1.5">
			<Checkbox
				id="vorversicherung"
				name="vorversicherung"
				checked={kunde?.vorversicherung ?? false}
			/>
			<Label for="vorversicherung">Lückenlose Vorversicherung</Label>
		</div>
	</div>
	<div class="grid gap-1.5">
		<Label for="status">Status</Label>
		<select
			id="status"
			name="status"
			class="h-9 rounded-md border border-input bg-background px-3 text-sm"
		>
			<option value="aktiv" selected={(kunde?.status ?? 'aktiv') === 'aktiv'}>aktiv</option>
			<option value="inaktiv" selected={kunde?.status === 'inaktiv'}>inaktiv</option>
		</select>
	</div>
	<div class="flex gap-3">
		<Button type="submit">Speichern</Button>
		<Button type="button" variant="outline" href={kunde ? `/kunden/${kunde.id}` : '/kunden'}
			>Abbrechen</Button
		>
	</div>
</form>
