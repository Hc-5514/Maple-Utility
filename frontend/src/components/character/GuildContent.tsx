import { useCharacterGuild } from '../../hooks/useCharacterDetail'

interface Props {
  characterId: number
  date: string
}

export default function GuildContent({ characterId, date }: Props) {
  const { data, isLoading } = useCharacterGuild(characterId, date)

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <h2 className="mb-3 font-semibold text-white">길드 콘텐츠</h2>

      {isLoading ? (
        <div className="h-4 w-32 animate-pulse rounded bg-white/10" />
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-white/40">기록 없음</p>
      ) : (
        <ul className="space-y-2">
          {data.map((record) => (
            <li key={record.id} className="flex items-center justify-between rounded-lg bg-[#1a1a2e] px-4 py-2.5">
              <span className="text-sm text-white/80">{record.contentName}</span>
              <span className="text-sm font-semibold text-[#4ade80]">
                {record.score !== null ? record.score.toLocaleString() : '미기록'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
