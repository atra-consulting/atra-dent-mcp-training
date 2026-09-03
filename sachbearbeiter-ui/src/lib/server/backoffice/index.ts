import type { BackofficeApi } from './api';
import { RestBackofficeApi } from './rest';

let instance: BackofficeApi | null = null;

export function backofficeApi(): BackofficeApi {
	if (!instance) instance = new RestBackofficeApi();
	return instance;
}
