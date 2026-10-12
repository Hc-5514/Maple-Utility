import ProgressBar from '../common/ProgressBar'
import { useCharacterDaily } from '../../hooks/useCharacterDetail'
import { detailCard, subtleRow } from './styles'

interface Props {
  characterId: number
  date: string
}

export default function DailyContent({ characterId, date }: Props) {
  const { data, isLoading, isError } = useCharacterDaily(characterId, date)

  return (
    <section className={`${detailCard} flex flex-col gap-4 p-6`}>
      <div className="flex items-center gap-3">
        <h2 className="text-[18px] font-bold">일일 컨텐츠</h2>
        <span className="ml-auto text-[13px] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">{date}</span>
      </div>

      {isLoading ? (
        [...Array(3)].map((_, i) => <div key={i} className={`${subtleRow} h-[72px] animate-pulse`} />)
      ) : isError ? (
        <p className="text-[14px] text-[var(--dash-danger)]">불러오는 중 오류가 발생했습니다.</p>
      ) : !data || data.length === 0 ? (
        <p className="text-[14px] text-[var(--dash-muted)]">기록 없음</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {data.map((record) => {
            const total = record.totalCount || 1
            const done = record.completedCount >= total
            const countColor = done
              ? 'text-[var(--dash-success)]'
              : record.completedCount === 0 ? 'text-[var(--dash-muted)]' : ''
            return (
              <li key={record.id} className={`${subtleRow} flex flex-col gap-3 px-[18px] py-4`}>
                <div className="flex items-center gap-2">
                  <span className="min-w-0 truncate text-[14px]">{record.contentName}</span>
                  <span className={`ml-auto text-[13px] font-semibold [font-family:Inter,sans-serif] ${countColor}`}>
                    {record.completedCount}/{record.totalCount}
                  </span>
                </div>
                <ProgressBar value={record.completedCount} max={total} />
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
