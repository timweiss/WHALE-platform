import * as z from 'zod';

// ids in paths refer to Postgres `integer` columns, anything outside that range
// would otherwise only fail inside the database query
export const EntityId = z.coerce.number().int().positive().max(2_147_483_647);
