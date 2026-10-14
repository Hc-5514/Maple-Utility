// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import client from '../api/client'
import { useCharacterBoss } from './useCharacterDetail'
import { seoulDate } from '../utils/date'

function Editor() {
  const { data } = useCharacterBoss(1, '2026-10-01', '2026-10-01')
  return <><output>{data ? String(data.weeklyCustomizable) : 'loading'}</output><input aria-label="편집 중 입력" defaultValue="기존 입력" /></>
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers() })

it('포커스 복귀에 따른 잠금 재조회 후 선택 주차와 입력 보존', async () => {
  vi.setSystemTime(new Date('2026-10-14T14:59:00Z'))
  const get = vi.spyOn(client, 'get').mockImplementation(async () => ({ data: { success: true, data: {
    weeklyBosses: [], monthlyBosses: [], weeklyCustomizable: seoulDate() === '2026-10-15', monthlyCustomizable: false,
  } } } as AxiosResponse))
  const queries = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  render(<QueryClientProvider client={queries}><Editor /></QueryClientProvider>)
  await screen.findByText('false')
  fireEvent.change(screen.getByLabelText('편집 중 입력'), { target: { value: '미저장 내용' } })
  vi.setSystemTime(new Date('2026-10-14T15:01:00Z'))
  act(() => { window.dispatchEvent(new Event('focus')) })
  await waitFor(() => expect(screen.getByText('true')).toBeTruthy())
  expect((screen.getByLabelText('편집 중 입력') as HTMLInputElement).value).toBe('미저장 내용')
  expect(get).toHaveBeenCalledTimes(2)
  expect(get).toHaveBeenLastCalledWith('/scheduler/1/boss?weeklyDate=2026-10-01&monthlyDate=2026-10-01')
})
