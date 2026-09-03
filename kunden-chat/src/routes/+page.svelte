<script lang="ts">
	import { untrack } from 'svelte';
	import { fade } from 'svelte/transition';
	import { browser } from '$app/environment';
	import { Chat } from '@ai-sdk/svelte';
	import FileText from '@lucide/svelte/icons/file-text';
	import Paperclip from '@lucide/svelte/icons/paperclip';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import X from '@lucide/svelte/icons/x';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { Textarea } from '$lib/components/ui/textarea';
	import * as Alert from '$lib/components/ui/alert';
	import TraceEntry from '$lib/components/chat/TraceEntry.svelte';
	import ViewBlock from '$lib/components/chat/view/ViewBlock.svelte';
	import { markdownToHtml } from '$lib/markdown';
	import { displayParts, author, type DisplayPart } from '$lib/trace';
	import type { RechnungsextraktionResult } from '$lib/types/api';
	import type { ChatUIMessage } from '$lib/types/chat';
	import { MAX_VIEWS, type View } from '$lib/types/view';

	let statusText = $state<string | null>(null);

	const chat = new Chat<ChatUIMessage>({
		onData: (part) => {
			if (part.type === 'data-status') statusText = part.data.text;
		}
	});

	const STORAGE_KEY = 'chat-debug';

	let debug = $state(browser && localStorage.getItem(STORAGE_KEY) === 'an');

	function toggleDebug(on: boolean) {
		debug = on;
		localStorage.setItem(STORAGE_KEY, on ? 'an' : 'aus');
	}

	let input = $state('');
	let historyElement = $state<HTMLDivElement | null>(null);
	let inputElement = $state<HTMLTextAreaElement | null>(null);

	const examples = [
		'Was zahlt brillant bei Implantaten?',
		'Welcher Tarif passt zu mir?',
		'Was zahlt balance bei Zahnersatz, und zu welcher Quote?',
		'Wie steht es um meinen Vertrag?',
		'Wie steht es um meine Rechnung?'
	];

	function messageText(message: ChatUIMessage): string {
		return message.parts.flatMap((part) => (part.type === 'text' ? [part.text] : [])).join('');
	}

	let fileElement = $state<HTMLInputElement | null>(null);
	let beleg = $state<RechnungsextraktionResult | null>(null);
	let belegFile = $state<string | null>(null);
	let belegLoading = $state(false);
	let belegError = $state<string | null>(null);

	const currency = (amount: string) =>
		new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(
			Number.parseFloat(amount)
		);

	const displayDate = (iso: string) => new Date(iso).toLocaleDateString('de-DE');

	async function uploadBeleg(file: File) {
		belegLoading = true;
		belegError = null;
		beleg = null;
		belegFile = file.name;
		try {
			const response = await fetch('/api/beleg', {
				method: 'POST',
				headers: { 'Content-Type': 'application/pdf' },
				body: file
			});
			const content = await response.json().catch(() => null);
			if (!response.ok) {
				belegError = content?.message ?? 'Der Beleg konnte nicht ausgewertet werden.';
				belegFile = null;
				return;
			}
			beleg = content as RechnungsextraktionResult;
		} catch {
			belegError = 'Der Beleg konnte nicht übertragen werden.';
			belegFile = null;
		} finally {
			belegLoading = false;
		}
	}

	function onFileChosen(event: Event) {
		const file = (event.currentTarget as HTMLInputElement).files?.[0];
		if (fileElement) fileElement.value = '';
		if (file) void uploadBeleg(file);
	}

	function removeBeleg() {
		beleg = null;
		belegFile = null;
		belegError = null;
	}

	const CHARS_PER_SECOND = 420;
	const MAX_DURATION_S = 4;

	let revealed = $state(0);
	let revealedId = $state<string | null>(null);

	const answer = $derived.by(() => {
		const message = chat.messages.at(-1);
		if (!message || message.role !== 'assistant') return null;
		return { id: message.id, text: messageText(message) };
	});

	const isStreaming = $derived(chat.status === 'submitted' || chat.status === 'streaming');
	const isTyping = $derived(answer !== null && revealed < answer.text.length);
	const isBusy = $derived(isStreaming || isTyping);

	const shownLength = $derived(
		answer && answer.id === revealedId ? Math.min(revealed, answer.text.length) : 0
	);
	const showStatus = $derived(isBusy && shownLength === 0);

	function revealedParts(message: ChatUIMessage): DisplayPart[] {
		const parts = displayParts(message);
		if (message.id !== revealedId || !isTyping) return parts;

		let remaining = revealed;
		return parts.map((part) => {
			if (part.kind !== 'text') return part;
			const visible = part.text.slice(0, remaining);
			remaining = Math.max(0, remaining - part.text.length);
			return { kind: 'text', text: visible };
		});
	}

	function views(message: ChatUIMessage): View[] {
		return message.parts
			.filter((part) => part.type === 'data-view')
			.map((part) => part.data)
			.slice(0, MAX_VIEWS);
	}

	type DisplayBlock =
		| { kind: 'text'; text: string; index: number }
		| { kind: 'trace'; traces: Extract<DisplayPart, { kind: 'trace' }>[] };

	function firstTimestamp(parts: DisplayPart[]): string | undefined {
		const found = parts.find(
			(part): part is Extract<DisplayPart, { kind: 'trace' }> =>
				part.kind === 'trace' && part.trace.timestamp !== undefined
		);
		return found?.trace.timestamp;
	}

	function blocks(parts: DisplayPart[]): DisplayBlock[] {
		const out: DisplayBlock[] = [];
		parts.forEach((part, index) => {
			if (part.kind === 'text') {
				out.push({ kind: 'text', text: part.text, index });
				return;
			}
			const last = out.at(-1);
			if (last?.kind === 'trace') last.traces.push(part);
			else out.push({ kind: 'trace', traces: [part] });
		});
		return out;
	}

	$effect(() => {
		const target = answer;
		if (!target) return;

		if (untrack(() => revealedId) !== target.id) {
			revealedId = target.id;
			revealed = 0;
		}
		if (untrack(() => revealed) >= target.text.length) return;

		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			revealed = target.text.length;
			return;
		}

		let frame = 0;
		let previous = performance.now();
		const step = (now: number) => {
			const shown = untrack(() => revealed);
			const speed = Math.max(CHARS_PER_SECOND, (target.text.length - shown) / MAX_DURATION_S);
			const chars = Math.max(1, Math.round(((now - previous) / 1000) * speed));
			previous = now;
			revealed = Math.min(shown + chars, target.text.length);
			if (revealed < target.text.length) frame = requestAnimationFrame(step);
		};
		frame = requestAnimationFrame(step);
		return () => cancelAnimationFrame(frame);
	});

	function statusLabel(text: string): string {
		const trimmed = text.trim();
		if (!trimmed) return '';
		const sentence = trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
		return /[.!?…]$/.test(sentence) ? sentence : `${sentence} …`;
	}

	function onEnterKey(event: KeyboardEvent) {
		if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
		event.preventDefault();
		send();
	}

	function submitMessage(event: SubmitEvent) {
		event.preventDefault();
		send();
	}

	function send() {
		const text = input.trim();
		if ((!text && !beleg) || isBusy || belegLoading) return;
		void chat.sendMessage(
			{ text, metadata: belegFile ? { belegFile } : undefined },
			beleg ? { body: { beleg } } : undefined
		);
		input = '';
		removeBeleg();
	}

	function cancel() {
		void chat.stop();
		if (answer) revealed = answer.text.length;
	}

	let clearing = $state(false);

	async function clearConversation() {
		clearing = true;
		try {
			const response = await fetch('/api/conversation/new', { method: 'POST' });
			if (!response.ok) return;
			chat.messages = [];
			chat.clearError();
			statusText = null;
			revealed = 0;
			revealedId = null;
			removeBeleg();
			input = '';
			inputElement?.focus();
		} catch {
		} finally {
			clearing = false;
		}
	}

	$effect(() => {
		if (!isStreaming) statusText = null;
	});

	$effect(() => {
		if (!isBusy) inputElement?.focus();
	});

	$effect(() => {
		void chat.messages.length;
		void shownLength;
		void statusText;
		void debug;
		if (historyElement) historyElement.scrollTop = historyElement.scrollHeight;
	});
</script>

{#snippet bubble(role: ChatUIMessage['role'], text: string, attachment?: string, typing = false)}
	<div class={role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
		<div
			class="max-w-[85%] rounded-lg px-4 py-2 text-sm
				{role === 'user' ? 'bg-primary whitespace-pre-wrap text-primary-foreground' : 'bg-muted'}"
		>
			{#if attachment}
				<span class="mb-1 flex items-center gap-1.5 text-xs opacity-80">
					<FileText class="size-3.5 shrink-0" aria-hidden="true" />
					{attachment}
				</span>
			{/if}
			{#if role === 'user'}
				{text}
			{:else}
				<!-- eslint-disable-next-line svelte/no-at-html-tags -->
				<div class="chat-prose {typing ? 'tippt' : ''}">{@html markdownToHtml(text)}</div>
			{/if}
		</div>
	</div>
{/snippet}

<div class="mx-auto flex h-full max-w-3xl flex-col px-4">
	<div class="flex shrink-0 items-center justify-end gap-4 pt-3 text-xs text-muted-foreground">
		{#if chat.messages.length > 0}
			<button
				type="button"
				onclick={() => void clearConversation()}
				disabled={clearing || isBusy}
				class="flex items-center gap-1.5 transition-colors hover:text-foreground disabled:opacity-40"
				title="Verlauf leeren und ein neues Gespräch beginnen"
			>
				<Trash2 class="size-3.5" aria-hidden="true" />
				Chat leeren
			</button>
		{/if}
		<label class="flex items-center gap-2 select-none">
			<Checkbox checked={debug} onchange={(e) => toggleDebug(e.currentTarget.checked)} />
			Agentenkommunikation anzeigen
		</label>
	</div>

	<div bind:this={historyElement} class="flex-1 space-y-4 overflow-y-auto py-6">
		{#if chat.messages.length === 0}
			<div class="mt-16 text-center">
				<img src="/avatare/atra-denta.svg" alt="" class="mx-auto mb-5 size-16" />
				<h1 class="text-akzent-verlauf text-3xl font-bold">Hallo, ich bin atra.denta!</h1>
				<p class="mt-2 text-sm text-muted-foreground">
					Ich beantworte Fragen zu unseren Zahntarifen und empfehle einen passenden Tarif.
				</p>
				<div class="mt-8 flex flex-wrap justify-center gap-2">
					{#each examples as example (example)}
						<button
							class="rounded-full border border-primary/25 bg-primary/5 px-4 py-2 text-sm text-primary transition-colors hover:border-akzent-hell hover:bg-akzent-hell/10"
							onclick={() => void chat.sendMessage({ text: example })}
						>
							{example}
						</button>
					{/each}
				</div>
			</div>
		{/if}

		{#each chat.messages as message (message.id)}
			{@const parts = revealedParts(message)}
			{@const attachment = message.metadata?.belegFile}
			{@const who = message.role === 'user' ? 'Sie → Chat-UI' : author(message)}

			{@const firstText = parts.findIndex((part) => part.kind === 'text' && part.text)}
			{@const lastText = parts.findLastIndex((part) => part.kind === 'text' && part.text)}

			{#if firstText === -1 && attachment}
				{@render bubble(message.role, '', attachment)}
			{/if}

			{@const start = firstTimestamp(parts)}

			{#each blocks(parts) as block, blockIndex (blockIndex)}
				{#if block.kind === 'trace'}
					{#if debug}
						<details open class="rounded-lg border border-border bg-muted/30 px-3 py-2">
							<summary
								class="flex cursor-pointer list-none items-center gap-2 text-[11px] font-medium text-muted-foreground select-none"
							>
								Agentenkommunikation · {block.traces.length}
								{block.traces.length === 1 ? 'Schritt' : 'Schritte'}
							</summary>
							<p class="mt-1 mb-2 text-[10px] text-muted-foreground/70">
								<span class="text-primary">A2A</span> Sprung zwischen Agenten ·
								<span class="text-akzent">MCP</span> Griff in ein System ·
								<span class="text-secondary-foreground">MODELL</span> Entscheidung im Modell ·
								<span class="font-medium text-akzent" aria-hidden="true">⇥</span> über den Transport gesendet,
								nicht vom Modell erzeugt
							</p>
							{#each block.traces as entry, i (i)}
								<TraceEntry
									trace={entry.trace}
									result={entry.result}
									failed={entry.failed}
									resultTime={entry.resultTime}
									level={entry.level}
									returnHop={entry.returnHop}
									{start}
								/>
							{/each}
						</details>
					{/if}
				{:else if block.text}
					{@const index = block.index}
					{#if debug && who && index === firstText}
						<p
							class="text-[11px] font-medium text-muted-foreground {message.role === 'user'
								? 'text-right'
								: ''}"
						>
							{who}
						</p>
					{/if}
					{@render bubble(
						message.role,
						block.text,
						index === firstText ? attachment : undefined,
						index === lastText && message.id === revealedId && isTyping
					)}
				{/if}
			{/each}

			{#if !(message.id === revealedId && isTyping)}
				{#each views(message) as view, i (i)}
					<ViewBlock {view} />
				{/each}
			{/if}
		{/each}

		<p class="sr-only" role="status" aria-live="polite">
			{showStatus && statusText ? statusLabel(statusText) : ''}
		</p>

		{#if showStatus}
			<div class="flex justify-start" transition:fade={{ duration: 150 }} aria-hidden="true">
				<div
					class="inline-flex items-center gap-2.5 rounded-full border border-primary/10 bg-muted px-3.5 py-2"
				>
					<span class="arbeitspunkt"></span>
					{#if statusText}
						{#key statusText}
							<span class="text-sm text-muted-foreground" in:fade={{ duration: 200 }}>
								{statusLabel(statusText)}
							</span>
						{/key}
					{/if}
				</div>
			</div>
		{/if}

		{#if chat.error}
			<Alert.Root variant="destructive">
				<Alert.Title
					>{chat.error.message ||
						'Ich bin gerade nicht erreichbar — bitte versuchen Sie es gleich noch einmal.'}</Alert.Title
				>
				<Alert.Description>
					<Button variant="outline" size="sm" class="mt-2" onclick={() => void chat.regenerate()}>
						Erneut versuchen
					</Button>
				</Alert.Description>
			</Alert.Root>
		{/if}
	</div>

	<div class="border-t py-4">
		{#if belegLoading || beleg || belegError}
			<div class="mb-3" transition:fade={{ duration: 150 }}>
				{#if belegError}
					<div
						class="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
					>
						<span class="flex-1">{belegError}</span>
						<button
							type="button"
							onclick={removeBeleg}
							class="shrink-0 rounded p-0.5 hover:bg-destructive/10"
							aria-label="Meldung schließen"
						>
							<X class="size-4" aria-hidden="true" />
						</button>
					</div>
				{:else if belegLoading}
					<div
						class="inline-flex items-center gap-2.5 rounded-full border border-primary/10 bg-muted px-3.5 py-2"
					>
						<span class="arbeitspunkt"></span>
						<span class="text-sm text-muted-foreground">Lese {belegFile} …</span>
					</div>
				{:else if beleg}
					<div class="rounded-lg border border-primary/20 bg-primary/5 px-3.5 py-2.5">
						<div class="flex items-start gap-2.5">
							<FileText class="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
							<div class="min-w-0 flex-1">
								<p class="truncate text-sm font-medium">{beleg.absender}</p>
								<p class="mt-0.5 text-xs text-muted-foreground">
									Rechnung {beleg.rechnungsnummer} vom {displayDate(beleg.rechnungsdatum)} ·
									{beleg.positionen.length}
									{beleg.positionen.length === 1 ? 'Position' : 'Positionen'} ·
									{currency(beleg.gesamtbetrag)}
								</p>
							</div>
							<button
								type="button"
								onclick={removeBeleg}
								class="shrink-0 rounded p-0.5 text-muted-foreground hover:bg-primary/10 hover:text-foreground"
								aria-label="Beleg entfernen"
							>
								<X class="size-4" aria-hidden="true" />
							</button>
						</div>
					</div>
				{/if}
			</div>
		{/if}

		<form onsubmit={submitMessage} class="flex items-end gap-2">
			<input
				bind:this={fileElement}
				type="file"
				accept="application/pdf"
				class="hidden"
				onchange={onFileChosen}
			/>
			<Button
				type="button"
				variant="outline"
				size="icon"
				disabled={isBusy || belegLoading}
				onclick={() => fileElement?.click()}
				aria-label="Rechnung als PDF anhängen"
				title="Rechnung als PDF anhängen"
			>
				<Paperclip class="size-4" aria-hidden="true" />
			</Button>
			<Textarea
				bind:ref={inputElement}
				placeholder={beleg ? 'Dazu schreiben (optional) …' : 'Nachricht eingeben …'}
				bind:value={input}
				disabled={isBusy}
				onkeydown={onEnterKey}
			/>
			{#if isBusy}
				<Button type="button" variant="outline" onclick={cancel}>Abbrechen</Button>
			{:else}
				<Button type="submit" class="bg-akzent-verlauf border-0 hover:opacity-90">Senden</Button>
			{/if}
		</form>
	</div>
</div>
