import { http, HttpResponse } from 'msw'
import { authHandlers } from './handlers/auth'
import { apiKeyHandlers } from './handlers/apiKey'
import { characterHandlers } from './handlers/character'
import { schedulerHandlers } from './handlers/scheduler'
import { bossHandlers } from './handlers/boss'
import { huntingHandlers } from './handlers/hunting'
import { statsHandlers } from './handlers/stats'

export const handlers = [
  http.get('/api/v1/health', () => HttpResponse.json({ success: true, data: 'ok' })),
  ...authHandlers,
  ...apiKeyHandlers,
  ...characterHandlers,
  ...schedulerHandlers,
  ...bossHandlers,
  ...huntingHandlers,
  ...statsHandlers,
]
