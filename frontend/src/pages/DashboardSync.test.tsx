// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import type { SyncJob } from '../types'
import DashboardPage from './DashboardPage'

const queryClients: QueryClient[] = []

afterEach(() => {
  cleanup()
  queryClients.splice(0).forEach((queryClient) => queryClient.clear())
  vi.restoreAllMocks()
})

function job(overrides: Partial<SyncJob> = {}): SyncJob {
  return {
    id: 1,
    jobType: 'SCHEDULER',
    status: 'COMPLETED',
    totalCount: 3,
    completedCount: 3,
    skippedCount: 0,
    failedCount: 0,
    errorMessage: null,
    startedAt: '2026-11-05T10:00:00+09:00',
    completedAt: '2026-11-05T10:00:10+09:00',
    ...overrides,
  }
}

function ok(data: unknown): AxiosResponse {
  return { data: { success: true, data } } as AxiosResponse
}

function renderPage(jobs: Partial<SyncJob>[], cachedNextJob?: SyncJob) {
  let started = 0
  let summaryReads = 0
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  queryClients.push(queryClient)
  if (cachedNextJob) {
    queryClient.setQueryData(['scheduler/sync-jobs', cachedNextJob.id], cachedNextJob)
  }
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
  const post = vi.spyOn(client, 'post').mockImplementation(async (url) => {
    if (!String(url).startsWith('/scheduler/sync?force=')) {
      throw new Error(`Unexpected POST: ${url}`)
    }
    return ok(jobs[started++])
  })
  vi.spyOn(client, 'get').mockImplementation(async (url) => {
    if (url === '/characters/world-selection') return ok({ worldNames: [] })
    if (url === '/scheduler/summary') {
      summaryReads++
      return ok({
        characters: [],
        syncedAt: summaryReads === 1 ? null : '2026-11-05T10:00:10+09:00',
      })
    }
    const selected = jobs.find((item) => url === `/scheduler/sync-jobs/${item.id}`)
    if (selected) return ok(selected)
    throw new Error(`Unexpected GET: ${url}`)
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { queryClient, post, invalidate, summaryReads: () => summaryReads }
}

describe('Dashboard scheduler synchronization', () => {
  it('announces partial failures and reloads the successfully synchronized data', async () => {
    const { summaryReads } = renderPage([
      job({ failedCount: 1, errorMessage: 'SCHEDULER_PARTIAL_FAILURE' }),
    ])

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('일부 캐릭터의 동기화에 실패했습니다. 실패 1명.')
    expect(alert.textContent).toContain('지금 동기화를 눌러 다시 시도해 주세요.')
    await waitFor(() => expect(summaryReads()).toBe(2))
    expect(screen.getByRole('button', { name: '지금 동기화' }).textContent)
      .toContain('2026.11.05 10:00')
    expect(screen.getByRole('button', { name: '지금 동기화' })).toHaveProperty('disabled', false)
  })

  it('retains the failure alert while fetching data saved before a fatal job failure', async () => {
    const { invalidate, summaryReads } = renderPage([
      job({ status: 'FAILED', completedCount: 2, failedCount: 1, errorMessage: 'API_KEY_INVALID' }),
    ])

    expect((await screen.findByRole('alert')).textContent).toBe('동기화에 실패했습니다.')
    await waitFor(() => expect(summaryReads()).toBe(2))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['scheduler/summary'] })
    expect(screen.queryByText(/일부 캐릭터의 동기화/)).toBeNull()
  })

  it('treats an omitted failedCount from an older server as zero failures', async () => {
    const legacyJob: Partial<SyncJob> = job()
    delete legacyJob.failedCount
    const { summaryReads } = renderPage([legacyJob])

    await waitFor(() => expect(summaryReads()).toBe(2))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('forces a retry and refreshes again when another completed job is already cached', async () => {
    const next = job({ id: 2 })
    const { post, invalidate, summaryReads, queryClient } = renderPage([
      job({ failedCount: 1, errorMessage: 'SCHEDULER_PARTIAL_FAILURE' }), next,
    ], next)
    await screen.findByRole('alert')
    await waitFor(() => expect(summaryReads()).toBe(2))

    fireEvent.click(screen.getByRole('button', { name: '지금 동기화' }))

    await waitFor(() => expect(post).toHaveBeenNthCalledWith(2, '/scheduler/sync?force=true'))
    expect(post).toHaveBeenNthCalledWith(1, '/scheduler/sync?force=false')
    await waitFor(() => expect(summaryReads()).toBe(3))
    expect(invalidate).toHaveBeenCalledTimes(2)
    expect(queryClient.getQueryData(['scheduler/sync-jobs', 2])).toEqual(next)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('keeps an unfinished job in progress and prevents duplicate synchronization', async () => {
    const { invalidate, summaryReads } = renderPage([
      job({ status: 'STARTED', totalCount: 4, completedCount: 3, failedCount: 1, completedAt: null }),
    ])

    await screen.findByText('동기화 중 3/4')
    expect(screen.getByRole('button', { name: '지금 동기화' })).toHaveProperty('disabled', true)
    expect(screen.queryByRole('alert')).toBeNull()
    expect(invalidate).not.toHaveBeenCalled()
    expect(summaryReads()).toBe(1)
  })
})
