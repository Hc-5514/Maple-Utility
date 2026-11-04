import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { huntingHandlers, resetHuntingRecords } from './hunting'

const server = setupServer(...huntingHandlers)
const get = (path: string) => fetch(`http://localhost/api/v1${path}`).then((response) => response.json())

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
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

  it('allows several records for the same character and date', async () => {
    const save = (mesoEarned: number) => fetch('http://localhost/api/v1/hunting', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ characterId: 1, recordDate: '2026-11-04', mesoEarned }),
    })

    const first = await save(100_000_000)
    const second = await save(200_000_000)

    expect(first.status).toBe(201)
    expect(second.status).toBe(201)
    const records = await get('/hunting?characterId=1&from=2026-11-04&to=2026-11-04')
    expect(records.data).toHaveLength(2)
    expect(records.data.map((record: { mesoEarned: number }) => record.mesoEarned).sort())
      .toEqual([100_000_000, 200_000_000])
    const moved = await fetch(`http://localhost/api/v1/hunting/${records.data[0].id}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ characterId: 1, recordDate: '2026-11-04', mesoEarned: 300_000_000 }),
    })
    expect(moved.status).toBe(200)
  })

  it('rejects unsupported page sizes', async () => {
    const response = await fetch('http://localhost/api/v1/hunting/page?page=0&size=15')
    expect(response.status).toBe(400)
  })
})
