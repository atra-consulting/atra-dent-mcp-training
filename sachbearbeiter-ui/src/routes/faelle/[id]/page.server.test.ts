import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '$lib/types/api';

const { updateSchadensfall, readSchadensfall, checkSchadensfall } = vi.hoisted(() => ({
	updateSchadensfall: vi.fn(),
	readSchadensfall: vi.fn(),
	checkSchadensfall: vi.fn()
}));

vi.mock('$lib/server/backoffice', () => ({
	backofficeApi: () => ({ updateSchadensfall, readSchadensfall })
}));
vi.mock('$lib/server/schadensfallagent', () => ({ checkSchadensfall }));

const { actions } = await import('./+page.server');

function formRequest() {
	const fields = new FormData();
	fields.set('kundenId', '9999');
	return { formData: async () => fields };
}

function invoke(id = '50071') {
	return actions.erneutPruefen({ params: { id }, request: formRequest() } as never);
}

const SCHADENSFALL = { id: 50071, kundenId: 4711, status: 'eingereicht' };

beforeEach(() => {
	updateSchadensfall.mockResolvedValue(SCHADENSFALL);
	checkSchadensfall.mockResolvedValue({
		outcome: 'geprueft',
		status: 'completed',
		text: 'Fall 50071: …'
	});
});

afterEach(() => {
	vi.clearAllMocks();
});

describe('erneutPruefen', () => {
	it('sends the Kundennummer OF THE SCHADENSFALL to the agent, not the one from the form', async () => {
		await invoke();

		expect(checkSchadensfall).toHaveBeenCalledWith(50071, 4711);
	});

	it('sets eingereicht first and calls the agent after', async () => {
		await invoke();

		expect(updateSchadensfall).toHaveBeenCalledWith(50071, {
			status: 'eingereicht',
			akteur: 'sachbearbeitung'
		});
		expect(updateSchadensfall.mock.invocationCallOrder[0]).toBeLessThan(
			checkSchadensfall.mock.invocationCallOrder[0]
		);
	});

	it('passes the answer of the agent on to the page', async () => {
		checkSchadensfall.mockResolvedValue({
			outcome: 'geprueft',
			status: 'completed',
			text: 'Fall 50071: Freigabe empfohlen.',
			empfehlung: 'freigabe'
		});

		const result = await invoke();

		expect(result).toEqual({
			done: true,
			agent: {
				outcome: 'geprueft',
				status: 'completed',
				text: 'Fall 50071: Freigabe empfohlen.',
				empfehlung: 'freigabe'
			}
		});
	});

	it('starts a Schadensfall that already stands on eingereicht too', async () => {
		updateSchadensfall.mockResolvedValue({ ...SCHADENSFALL, status: 'eingereicht' });

		await invoke();

		expect(updateSchadensfall).toHaveBeenCalledWith(50071, {
			status: 'eingereicht',
			akteur: 'sachbearbeitung'
		});
		expect(checkSchadensfall).toHaveBeenCalledWith(50071, 4711);
	});

	it('does not call the agent where there is no Schadensfall', async () => {
		updateSchadensfall.mockResolvedValue(null);

		const result = await invoke();

		expect(checkSchadensfall).not.toHaveBeenCalled();
		expect(result).toMatchObject({ status: 404 });
	});

	it('reports an error of the Kernsystem without calling the agent', async () => {
		updateSchadensfall.mockRejectedValue(
			new ApiError({
				title: 'Konflikt',
				status: 409,
				detail: 'Der Fall wurde inzwischen geändert.'
			})
		);

		const result = await invoke();

		expect(checkSchadensfall).not.toHaveBeenCalled();
		expect(result).toMatchObject({
			status: 409,
			data: { error: 'Der Fall wurde inzwischen geändert.' }
		});
	});
});
