<script lang="ts">
	import Music from '@lucide/svelte/icons/music';
	let player = $state<HTMLAudioElement>();

	$effect(() => {
		player?.play().catch(() => {});
	});
</script>

<div class="flex items-center gap-3 p-3">
	<Music class="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
	<div class="min-w-0 flex-1">
		<h3 class="truncate text-sm font-medium">Heal Your Teeth</h3>
		<p class="truncate text-xs text-muted-foreground">Die atra.dent-Hymne</p>
		<!-- svelte-ignore a11y_media_has_caption -->
		<audio bind:this={player} class="mt-2 w-full" controls preload="auto" src="/atra-dent-hymne.mp3"
		></audio>
	</div>
</div>
