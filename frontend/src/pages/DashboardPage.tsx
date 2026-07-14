import { useSchedulerSummary } from '../hooks/useScheduler'
import CharacterSlider from '../components/dashboard/CharacterSlider'

export default function DashboardPage() {
  const { data, isLoading, isError } = useSchedulerSummary()

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
      <div className="flex items-baseline justify-between">
        <h1 className="text-2xl font-bold text-white">대시보드</h1>
        <span className="text-xs text-white/30">
          마지막 동기화: {new Date(data.syncedAt).toLocaleString('ko-KR')}
        </span>
      </div>

      <CharacterSlider characters={data.characters} />
    </div>
  )
}
