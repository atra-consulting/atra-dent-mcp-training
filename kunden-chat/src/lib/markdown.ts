
const ESCAPES: Record<string, string> = {
	'&': '&amp;',
	'<': '&lt;',
	'>': '&gt;',
	'"': '&quot;',
	"'": '&#39;'
};

function escapeHtml(text: string): string {
	return text.replace(/[&<>"']/g, (char) => ESCAPES[char]);
}

const BULLET = /^\s{0,3}[-*+]\s+(.*)$/;
const NUMBERED = /^\s{0,3}\d{1,9}[.)]\s+(.*)$/;

interface Block {
	kind: 'paragraph' | 'ul' | 'ol';
	items: string[];
}

function blocks(text: string): Block[] {
	const collected: Block[] = [];
	let open: Block | null = null;

	for (const line of text.split('\n')) {
		if (!line.trim()) {
			open = null;
			continue;
		}

		const bullet = BULLET.exec(line);
		const numbered = bullet ? null : NUMBERED.exec(line);
		const kind = bullet ? 'ul' : numbered ? 'ol' : 'paragraph';

		if (open === null || open.kind !== kind) {
			open = { kind, items: [] };
			collected.push(open);
		}

		open.items.push(bullet?.[1] ?? numbered?.[1] ?? line);
	}

	return collected;
}

function dropIncompleteMarkup(text: string): string {
	return (
		text
			.replace(/\[([^\]]*)\](\([^)]*)?$/, '$1')
			.replace(/\[([^\]]*)$/, '$1')
			.replace(/(\*\*|\*|`)(?=\S)[^`*]*$/, (match, marker: string) =>
				match.slice(marker.length)
			)
	);
}

function inline(text: string): string {
	const codeSpans: string[] = [];
	let result = text.replace(/`([^`\n]+)`/g, (_match, content: string) => {
		codeSpans.push(content);
		return `\u0000${codeSpans.length - 1}\u0000`;
	});

	result = result
		.replace(
			/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
			'<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
		)
		.replace(/\*\*(?=\S)([\s\S]*?\S)\*\*/g, '<strong>$1</strong>')
		.replace(/(?<![*\w])\*(?=\S)([^*\n]*?\S)\*(?!\*)/g, '<em>$1</em>');

	result = dropIncompleteMarkup(result);

	return result.replace(
		// eslint-disable-next-line no-control-regex
		/\u0000(\d+)\u0000/g,
		(_match, index: string) => `<code>${codeSpans[Number(index)]}</code>`
	);
}

export function markdownToHtml(text: string): string {
	return blocks(escapeHtml(text))
		.map((block) => {
			if (block.kind === 'paragraph') {
				return `<p>${inline(block.items.join('\n')).replace(/\n/g, '<br>')}</p>`;
			}
			const items = block.items.map((bullet) => `<li>${inline(bullet)}</li>`).join('');
			return `<${block.kind}>${items}</${block.kind}>`;
		})
		.join('');
}
