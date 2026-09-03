<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';
	import { schadensfallstatus, schadensfallstatusVariant } from '$lib/format';

	let { status }: { status: string } = $props();
</script>

<Badge variant={schadensfallstatusVariant(status)}>{schadensfallstatus(status)}</Badge>
