import { useCharacterWeekly } from '../../hooks/useCharacterDetail'

interface Props {
  characterId: number
  weekStart: string
}

export default function WeeklyContent({ characterId, weekStart }: Props) {
  const { data, isLoading } = useCharacterWeekly(characterId, weekStart)

  const allDone = data && data.length > 0 && data.every((r) => r.completed)

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <div className="mb-3 flex items-center gap-2">
        <h2 className="font-semibold text-white">주간 컨텐츠</h2>
        {allDone && <span className="text-[#4ade80]">✓</span>}
        <span className="ml-auto text-xs text-white/40">주간 {weekStart} ~</span>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-10 animate-pulse rounded bg-white/10" />
          ))}
        </div>
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-white/40">기록 없음</p>
      ) : (
        <ul className="space-y-2">
          {data.map((record) => (
            <li
              key={record.id ?? record.contentName}
              className="flex items-center gap-3 rounded-lg bg-[#1a1a2e] px-4 py-3"
            >
              <span
                className={`text-lg leading-none ${record.completed ? 'text-[#4ade80]' : 'text-white/20'}`}
              >
                {record.completed ? '✓' : '─'}
              </span>
              <span className={`flex-1 text-sm ${record.completed ? 'text-white/60 line-through' : 'text-white/80'}`}>
                {record.contentName}
              </span>
              {record.score !== null && (
                <span className="text-xs text-white/40">점수 {record.score}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
