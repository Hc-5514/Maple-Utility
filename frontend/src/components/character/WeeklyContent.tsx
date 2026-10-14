import { useCharacterWeekly } from '../../hooks/useCharacterDetail'
import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from './assets'
import { detailCard, subtleRow } from './styles'

interface Props {
  characterId: number
  weekStart: string
}

export default function WeeklyContent({ characterId, weekStart }: Props) {
  const { data, isLoading, isError } = useCharacterWeekly(characterId, weekStart)
  const assets = characterAssets[useThemeStore().theme]

  return (
    <section className={`${detailCard} flex flex-col gap-4 p-6`}>
      <div className="flex items-center gap-3">
        <h2 className="text-[18px] font-bold">주간 컨텐츠</h2>
        <span className="ml-auto text-[13px] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">{weekStart}</span>
      </div>

      {isLoading ? (
        [...Array(3)].map((_, i) => <div key={i} className={`${subtleRow} h-[52px] animate-pulse`} />)
      ) : isError ? (
        <p className="text-[14px] text-[var(--dash-danger)]">불러오는 중 오류가 발생했습니다.</p>
      ) : !data || data.length === 0 ? (
        <p className="text-[14px] text-[var(--dash-muted)]">기록 없음</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {data.map((record) => (
            <li key={record.id ?? record.contentName} className={`${subtleRow} flex h-[52px] items-center gap-3 px-[18px]`}>
              {record.completed ? (
                <span
                  aria-label="완료"
                  className="flex size-[18px] shrink-0 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--dash-success)_14%,transparent)]"
                >
                  <img src={assets.imgCheck} alt="" className="size-3" />
                </span>
              ) : (
                <img src={assets.imgCheckCircle} alt="미완료" className="size-[18px] shrink-0" />
              )}
              <span className="min-w-0 truncate text-[14px]">{record.contentName}</span>
              {record.score !== null && (
                <span className="ml-auto shrink-0 text-[13px] text-[var(--dash-muted)]">
                  점수 {record.score.toLocaleString()}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
