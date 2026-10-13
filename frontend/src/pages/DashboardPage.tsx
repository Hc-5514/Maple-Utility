import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import type { ApiResponse, SyncJob } from '../types'
import { useSchedulerSummary } from '../hooks/useScheduler'
import { useSaveWorldSelection, useWorldSelection } from '../hooks/useWorldSelection'
import { useSelectedWeek } from '../hooks/useSelectedWeek'
import { useThemeStore } from '../stores/themeStore'
import { dashboardAssets } from '../components/dashboard/assets'
import CharacterSlider from '../components/dashboard/CharacterSlider'
import WeeklyPeriodSelect from '../components/dashboard/WeeklyPeriodSelect'
import WorldSelectionModal from '../components/dashboard/WorldSelectionModal'

export default function DashboardPage() {
  const queryClient = useQueryClient()
  const { theme } = useThemeStore()
  const assets = dashboardAssets[theme]
  const [jobId, setJobId] = useState<number | null>(null)
  const { week, weeks, setWeek } = useSelectedWeek()
  const [manageOpen, setManageOpen] = useState(false)
  const { data, isLoading, isError } = useSchedulerSummary(week.date)
  const worlds = useWorldSelection()
  const saveWorlds = useSaveWorldSelection()
  const syncMutation = useMutation({
    mutationFn: async (force: boolean) => {
      const { data: response } = await client.post<ApiResponse<SyncJob>>(
        `/scheduler/sync?force=${force}`
      )
      return response.data
    },
    onSuccess: (job) => setJobId(job.id),
  })
  const { data: syncJob } = useQuery({
    queryKey: ['scheduler/sync-jobs', jobId],
    queryFn: async () => {
      const { data: response } = await client.get<ApiResponse<SyncJob>>(
        `/scheduler/sync-jobs/${jobId}`
      )
      return response.data
    },
    enabled: jobId !== null,
    refetchInterval: (query) => (query.state.data?.status === 'STARTED' ? 2000 : false),
  })
  useEffect(() => {
    syncMutation.mutate(false)
    // Only request freshness once on dashboard entry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    if (syncJob?.status === 'COMPLETED')
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
  }, [syncJob?.status, queryClient])
  const selectedWorlds = worlds.data?.worldNames ?? []
  const characters = (data?.characters ?? []).filter(
    (character) => character.worldName && selectedWorlds.includes(character.worldName)
  )
  const syncedAt = data?.syncedAt
    ? new Intl.DateTimeFormat('sv-SE', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      })
        .format(new Date(data.syncedAt))
        .replace(/-/g, '.')
    : '동기화 기록 없음'
  return (
    <div className="flex flex-col gap-7">
      <div className="flex min-h-[90px] flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-[7px]">
          <p className="text-[11px] font-semibold leading-[13px] text-[var(--dash-accent)] [font-family:Inter,sans-serif]">
            MY MAPLE / OVERVIEW
          </p>
          <h1 className="text-[34px] font-bold leading-[1.25]">오늘도, 한 걸음 더.</h1>
          <p className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">
            캐릭터별 일일 루틴부터 보스 진행 상황까지 한눈에 관리하세요.
          </p>
        </div>
        <div className="flex flex-col items-end gap-3">
          <div className="flex items-center gap-2 text-[12px] text-[var(--dash-muted)]">
            <button
              type="button"
              onClick={() => syncMutation.mutate(true)}
              disabled={syncMutation.isPending || syncJob?.status === 'STARTED'}
              aria-label="지금 동기화"
              className="rounded-md text-left disabled:opacity-60"
            >
              {syncJob?.status === 'STARTED'
                ? `동기화 중 ${syncJob.completedCount}/${syncJob.totalCount}`
                : `마지막 동기화 · ${syncedAt}`}
            </button>
          </div>
          <button
            type="button"
            disabled={worlds.isLoading || worlds.isError}
            onClick={() => setManageOpen(true)}
            className="flex h-11 items-center gap-2 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-[18px] text-[14px] font-bold disabled:opacity-40"
          >
            <img src={assets.imgControlIcon2} alt="" />
            월드 선택
          </button>
        </div>
      </div>
      <div className="relative z-20 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] py-[17px] pr-[17px] pl-[24px] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px]">
        <div className="flex items-center gap-4">
          <h2 className="text-[16px]">내 캐릭터</h2>
          <span className="rounded-full border border-[var(--dash-accent)]/16 bg-[var(--dash-accent)]/8 px-[10px] py-1 text-[11px] font-bold text-[var(--dash-accent)]">
            {characters.length} 캐릭터
          </span>
        </div>
        <WeeklyPeriodSelect
          value={week.date}
          onChange={(date) => setWeek(weeks.find((item) => item.date === date)?.start ?? weeks[0].start)}
        />
      </div>
      {(syncMutation.isError || syncJob?.status === 'FAILED') && (
        <p role="alert" className="text-sm text-red-500">
          동기화에 실패했습니다.
        </p>
      )}
      {isLoading || worlds.isLoading ? (
        <p role="status" className="py-16 text-[var(--dash-muted)]">
          불러오는 중...
        </p>
      ) : isError || worlds.isError || !data ? (
        <div role="alert" className="py-16 text-center">
          <p className="text-red-500">대시보드 데이터를 불러오지 못했습니다.</p>
          <button
            type="button"
            onClick={() => {
              void worlds.refetch()
              void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
            }}
            className="mt-4 min-h-11 text-[var(--dash-accent)]"
          >
            다시 시도
          </button>
        </div>
      ) : selectedWorlds.length === 0 ? (
        <div className="rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-16 text-center">
          <p className="font-bold">선택한 월드가 없습니다</p>
          <p className="mt-2 text-sm text-[var(--dash-muted)]">
            캐릭터를 표시할 월드를 선택해 주세요.
          </p>
          <button
            type="button"
            onClick={() => setManageOpen(true)}
            className="mt-4 min-h-11 rounded-[10px] bg-[var(--dash-accent)] px-5 font-bold text-[var(--dash-button-text)]"
          >
            월드 선택
          </button>
        </div>
      ) : (
        <CharacterSlider key={selectedWorlds.join(',')} characters={characters} />
      )}
      <div className="flex items-start gap-[10px] text-[12px] leading-[17px] text-[var(--dash-muted)]">
        <img src={assets.imgControlIcon4} alt="" />
        일일 콘텐츠는 매일 오전 0시, 주간 보스는 목요일에 초기화됩니다. 0/0은 해당 캐릭터의 비대상
        콘텐츠입니다.
      </div>
      {manageOpen && (
        <WorldSelectionModal
          selectedWorlds={selectedWorlds}
          onClose={() => setManageOpen(false)}
          onSave={(selection) => saveWorlds.mutateAsync(selection)}
        />
      )}
    </div>
  )
}
