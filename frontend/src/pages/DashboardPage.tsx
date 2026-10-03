import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import client from '../api/client'
import type { ApiResponse, SyncJob } from '../types'
import { useSchedulerSummary } from '../hooks/useScheduler'
import CharacterSlider from '../components/dashboard/CharacterSlider'

export default function DashboardPage() {
  const queryClient = useQueryClient()
  const [jobId, setJobId] = useState<number | null>(null)
  const { data, isLoading, isError } = useSchedulerSummary()

  const syncMutation = useMutation({
    mutationFn: async (force: boolean) => {
      const { data: response } = await client.post<ApiResponse<SyncJob>>(`/scheduler/sync?force=${force}`)
      return response.data
    },
    onSuccess: (job) => setJobId(job.id),
  })

  const { data: syncJob } = useQuery({
    queryKey: ['scheduler/sync-jobs', jobId],
    queryFn: async () => {
      const { data: response } = await client.get<ApiResponse<SyncJob>>(`/scheduler/sync-jobs/${jobId}`)
      return response.data
    },
    enabled: jobId !== null,
    refetchInterval: (query) => query.state.data?.status === 'STARTED' ? 2000 : false,
  })

  useEffect(() => {
    syncMutation.mutate(false)
    // Only request freshness once on dashboard entry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (syncJob?.status === 'COMPLETED') {
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
    }
  }, [syncJob?.status, queryClient])

  if (isLoading) {
    return (
      <div className="flex items-center gap-3 py-16 text-white/50">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/30 border-t-white/80" />
        불러오는 중...
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="py-16 text-center">
        <p className="text-[#f87171]">대시보드 데이터를 불러오지 못했습니다.</p>
        <p className="mt-1 text-sm text-white/40">잠시 후 다시 시도해 주세요.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-white">대시보드</h1>
        <div className="flex items-center gap-3">
          <span className="text-xs text-white/50">
            {syncJob?.status === 'STARTED'
              ? `동기화 중 ${syncJob.totalCount > 0 ? `${syncJob.completedCount}/${syncJob.totalCount}` : ''}`
              : `마지막 동기화: ${data.syncedAt ? new Date(data.syncedAt).toLocaleString('ko-KR') : '동기화 기록 없음'}`}
          </span>
          <button
            type="button"
            onClick={() => syncMutation.mutate(true)}
            disabled={syncMutation.isPending || syncJob?.status === 'STARTED'}
            aria-label="지금 동기화"
            title="지금 동기화"
            className="p-1 text-white/60 hover:text-white disabled:opacity-40"
          >
            <RefreshCw size={16} />
          </button>
        </div>
      </div>

      {(syncMutation.isError || syncJob?.status === 'FAILED') && (
        <p className="text-sm text-[#f87171]">동기화에 실패했습니다.</p>
      )}

      <CharacterSlider characters={data.characters} />
    </div>
  )
}
