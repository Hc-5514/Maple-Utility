import ProgressBar from '../common/ProgressBar'
import { useCharacterDaily } from '../../hooks/useCharacterDetail'

interface Props {
  characterId: number
  date: string
}

export default function DailyContent({ characterId, date }: Props) {
  const { data, isLoading } = useCharacterDaily(characterId, date)

  const allDone =
    data && data.length > 0 && data.every((r) => r.completedCount >= r.totalCount)

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <div className="mb-3 flex items-center gap-2">
        <h2 className="font-semibold text-white">일일 컨텐츠</h2>
        {allDone && <span className="text-[#4ade80]">✓</span>}
        <span className="ml-auto text-xs text-white/40">{date}</span>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-white/10" />
          ))}
        </div>
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-white/40">기록 없음</p>
      ) : (
        <ul className="space-y-2.5">
          {data.map((record) => (
            <li key={record.id} className="rounded-lg bg-[#1a1a2e] px-4 py-3">
              <div className="mb-1.5 flex items-center justify-between">
                <span className="text-sm text-white/80">{record.contentName}</span>
                <span className="text-xs text-white/50">
                  {record.completedCount}/{record.totalCount}
                </span>
              </div>
              <ProgressBar value={record.completedCount} max={record.totalCount || 1} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
