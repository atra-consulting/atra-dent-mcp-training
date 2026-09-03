import { describe, expect, it } from 'vitest';
import { markdownToHtml } from './markdown';

describe('escapeHtml', () => {
	it('turns markup into text before anything is interpreted', () => {
		const html = markdownToHtml('<script>alert(1)</script>');
		expect(html).not.toContain('<script>');
		expect(html).toContain('&lt;script&gt;');
	});

	it('does not let a quotation mark break out of an attribute', () => {
		expect(markdownToHtml('er sagte "hallo"')).toContain('&quot;hallo&quot;');
	});

	it('escapes the ampersand in an address too', () => {
		expect(markdownToHtml('[dort](https://atra.dent/x?a=1&b=2)')).toContain(
			'href="https://atra.dent/x?a=1&amp;b=2"'
		);
	});
});

describe('inline', () => {
	it('sets bold', () => {
		expect(markdownToHtml('Der **brillant** zahlt.')).toBe(
			'<p>Der <strong>brillant</strong> zahlt.</p>'
		);
	});

	it('sets italic', () => {
		expect(markdownToHtml('Der *brillant* zahlt.')).toBe('<p>Der <em>brillant</em> zahlt.</p>');
	});

	it('sets code', () => {
		expect(markdownToHtml('Siehe `§ 4`.')).toBe('<p>Siehe <code>§ 4</code>.</p>');
	});

	it('interprets nothing further inside a code span', () => {
		expect(markdownToHtml('`**nicht fett**`')).toBe('<p><code>**nicht fett**</code></p>');
	});

	it('links http and https', () => {
		expect(markdownToHtml('[Bedingungen](https://atra.dent/b)')).toBe(
			'<p><a href="https://atra.dent/b" target="_blank" rel="noopener noreferrer">Bedingungen</a></p>'
		);
	});

	it('links no other scheme', () => {
		const html = markdownToHtml('[klick](javascript:alert(1))');
		expect(html).not.toContain('<a ');
		expect(html).not.toContain('href');
	});

	it('does not take an asterisk between spaces for markup', () => {
		expect(markdownToHtml('5 * 3 Sitzungen')).toBe('<p>5 * 3 Sitzungen</p>');
	});
});

describe('blocks', () => {
	it('separates paragraphs at the blank line', () => {
		expect(markdownToHtml('Eins\n\nZwei')).toBe('<p>Eins</p><p>Zwei</p>');
	});

	it('turns a single newline into a line break', () => {
		expect(markdownToHtml('Eins\nZwei')).toBe('<p>Eins<br>Zwei</p>');
	});

	it('sets a bullet list', () => {
		expect(markdownToHtml('- eins\n- zwei')).toBe('<ul><li>eins</li><li>zwei</li></ul>');
	});

	it('takes the asterisk as a bullet too', () => {
		expect(markdownToHtml('* eins\n* zwei')).toBe('<ul><li>eins</li><li>zwei</li></ul>');
	});

	it('sets a numbered list', () => {
		expect(markdownToHtml('1. eins\n2. zwei')).toBe('<ol><li>eins</li><li>zwei</li></ol>');
	});

	it('closes the list when prose comes again', () => {
		expect(markdownToHtml('- eins\n\nDanach')).toBe('<ul><li>eins</li></ul><p>Danach</p>');
	});

	it('interprets inline markup inside a list item too', () => {
		expect(markdownToHtml('- der **brillant**')).toBe(
			'<ul><li>der <strong>brillant</strong></li></ul>'
		);
	});

	it('sets no heading and leaves the hashes standing', () => {
		expect(markdownToHtml('## Tarife')).toBe('<p>## Tarife</p>');
	});
});

describe('truncated', () => {

	it('swallows a bold that has only been started', () => {
		expect(markdownToHtml('Der **bril')).toBe('<p>Der bril</p>');
	});

	it('swallows an italic that has only been started', () => {
		expect(markdownToHtml('Der *bril')).toBe('<p>Der bril</p>');
	});

	it('swallows a code span that has only been started', () => {
		expect(markdownToHtml('Siehe `§ 4 Wart')).toBe('<p>Siehe § 4 Wart</p>');
	});

	it('leaves what is finished before it standing', () => {
		expect(markdownToHtml('**eins** und **zw')).toBe('<p><strong>eins</strong> und zw</p>');
	});

	it('shows the label already while a link is still being written', () => {
		expect(markdownToHtml('siehe [Bedingun')).toBe('<p>siehe Bedingun</p>');
		expect(markdownToHtml('siehe [Bedingungen](https://atra')).toBe('<p>siehe Bedingungen</p>');
	});
});

describe('edge cases', () => {
	it('returns nothing for empty text', () => {
		expect(markdownToHtml('')).toBe('');
		expect(markdownToHtml('   \n\n  ')).toBe('');
	});
});
