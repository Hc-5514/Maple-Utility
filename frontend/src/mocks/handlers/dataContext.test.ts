import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { authHandlers } from './auth'
import { apiKeyHandlers } from './apiKey'
import { dataContextHandlers } from './dataContext'
import { resetDataContextStore } from './dataContextStore'

const server = setupServer(...authHandlers, ...apiKeyHandlers, ...dataContextHandlers)
const base = 'http://localhost/api/v1'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetDataContextStore('multi'))
afterAll(() => server.close())

const list = async () => (await fetch(`${base}/auth/data-contexts`)).json()
const post = (path: string, body: unknown) =>
  fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })

describe('다중 넥슨 계정 목', () => {
  it('현재 사용 중 계정을 먼저, 이후 연결순으로 계정당 키 3/2/1개를 응답한다', async () => {
    const rows = await list()
    const order = rows.map((row: { contextId: number }) => row.contextId)
    expect([...new Set(order)]).toEqual([1, 2, 3])
    expect(rows.filter((row: { contextId: number }) => row.contextId === 1)).toHaveLength(3)
    expect(rows.filter((row: { contextId: number }) => row.contextId === 2)).toHaveLength(2)
    expect(rows.filter((row: { contextId: number }) => row.contextId === 3)).toHaveLength(1)
    expect(rows.find((row: { status: string }) => row.status === 'EXPIRED')).toBeTruthy()
    expect(rows.every((row: { linkedAt: string | null }) => row.linkedAt)).toBe(true)
  })

  it('계정을 전환하면 해당 계정이 맨 앞에 오고 나머지는 연결순을 유지한다', async () => {
    await post('/auth/data-context', { credentialId: 30 })
    const order = (await list()).map((row: { contextId: number }) => row.contextId)
    expect([...new Set(order)]).toEqual([3, 1, 2])
  })

  it('사전 확인은 데이터를 바꾸지 않고 상태별로 응답한다', async () => {
    const before = await list()
    const statuses = await Promise.all(
      ['live_new_key_0001', 'live_other-kakao_0002', 'live_existing_9f2a'].map(async (apiKey) =>
        (await (await post('/auth/data-contexts/precheck', { apiKey })).json()).status
      )
    )
    expect(statuses).toEqual(['AVAILABLE', 'LINKED_TO_OTHER_KAKAO', 'ALREADY_LINKED'])
    expect(await list()).toEqual(before)
  })

  it('최대 5개에서 새 계정 추가는 409, 6번째 사전 확인은 LIMIT_EXCEEDED', async () => {
    await post('/api-key', { apiKey: 'live_new_account_0004' })
    await post('/api-key', { apiKey: 'live_new_account_0005' })
    const check = await (await post('/auth/data-contexts/precheck', { apiKey: 'live_new_account_0006' })).json()
    expect(check).toMatchObject({ status: 'LIMIT_EXCEEDED', connectedCount: 5, maxCount: 5 })
    const rejected = await post('/api-key', { apiKey: 'live_new_account_0006' })
    expect(rejected.status).toBe(409)
    expect((await rejected.json()).code).toBe('DATA_CONTEXT_LIMIT_EXCEEDED')
  })

  it('다른 카카오에 연결된 키 추가는 409 DATA_CONTEXT_CONFLICT', async () => {
    const response = await post('/api-key', { apiKey: 'live_other-kakao_0009' })
    expect(response.status).toBe(409)
    expect((await response.json()).code).toBe('DATA_CONTEXT_CONFLICT')
  })

  it('연결 해제와 데이터 삭제는 계정을 제거하고 현재 계정이면 다음 계정으로 전환한다', async () => {
    const unlinked = await fetch(`${base}/auth/data-contexts/1/link`, { method: 'DELETE' })
    expect(unlinked.status).toBe(200)
    let rows = await list()
    expect([...new Set(rows.map((row: { contextId: number }) => row.contextId))]).toEqual([2, 3])
    expect(rows.some((row: { current: boolean }) => row.current)).toBe(true)

    const deleted = await fetch(`${base}/auth/data-contexts/2`, { method: 'DELETE' })
    expect(deleted.status).toBe(200)
    rows = await list()
    expect([...new Set(rows.map((row: { contextId: number }) => row.contextId))]).toEqual([3])
    expect(rows.find((row: { current: boolean }) => row.current).contextId).toBe(3)
  })
})
