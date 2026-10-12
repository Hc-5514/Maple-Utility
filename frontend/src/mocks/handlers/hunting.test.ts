import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { huntingHandlers, resetHuntingRecords } from './hunting'

const server = setupServer(...huntingHandlers)
const get = (path: string) => fetch(`http://localhost/api/v1${path}`).then((response) => response.json())

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => resetHuntingRecords())
afterAll(() => server.close())

describe('hunting mock handlers', () => {
  it('serves the ground catalog before the record id route', async () => {
    const grounds = await get('/hunting/grounds')
    expect(grounds.success).toBe(true)
    expect(grounds.data.length).toBeGreaterThan(100)
    expect(grounds.data.filter((ground: { regionName: string }) => ground.regionName === '탈라하트')[0].mapName)
      .toBe('환영의 길 4')
  })

  it('pages records newest first and summarizes the whole filter', async () => {
    const page = await get('/hunting/page?from=2026-09-01&to=2026-09-30&page=1&size=10')
    expect(page.data.totalElements).toBe(20)
    expect(page.data.totalPages).toBe(2)
    expect(page.data.content[0].recordDate).toBe('2026-09-20')

    const summary = await get('/hunting/summary?from=2026-09-01&to=2026-09-30')
    expect(summary.data).toEqual({
      recordCount: 20,
      totalMeso: 4_325_000_000,
      totalSolErda: 217,
      totalPlayDurationMin: 2250,
    })
  })

  it('rejects unsupported page sizes', async () => {
    const response = await fetch('http://localhost/api/v1/hunting/page?page=0&size=15')
    expect(response.status).toBe(400)
  })
})
