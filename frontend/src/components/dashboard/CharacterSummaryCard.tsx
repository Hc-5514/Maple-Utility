import { Link } from 'react-router-dom'
import type { CharacterSchedulerSummary } from '../../types'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from './assets'
import { worlds } from './worlds'
const progressItems = [
  { key: 'daily', label: '일일' },
  { key: 'weekly', label: '주간' },
  { key: 'weeklyBoss', label: '주보스' },
  { key: 'monthlyBoss', label: '월보스' },
] as const
const avatarColors = [
  'border-[rgba(196,173,255,0.21)] bg-[rgba(196,173,255,0.09)]',
  'border-[rgba(143,209,239,0.21)] bg-[rgba(143,209,239,0.09)]',
  'border-[rgba(234,168,159,0.21)] bg-[rgba(234,168,159,0.09)]',
  'border-[rgba(230,198,160,0.21)] bg-[rgba(230,198,160,0.09)]',
  'border-[rgba(234,174,212,0.21)] bg-[rgba(234,174,212,0.09)]',
  'border-[rgba(145,196,245,0.21)] bg-[rgba(145,196,245,0.09)]',
]
export default function CharacterSummaryCard({
  character,
  index = 0,
}: {
  character: CharacterSchedulerSummary
  index?: number
}) {
  const { theme } = useThemeStore()
  const assets = dashboardAssets[theme]
  const world = worlds.find(([name]) => name === character.worldName)
  const nonTarget = progressItems.some(({ key }) => character[key].total === 0)
  return (
    <article
      className={`relative flex h-full flex-col gap-[23px] rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[23px] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px] ${nonTarget ? 'min-h-[339px]' : 'min-h-[322px]'}`}
    >
      <div className="flex items-center gap-4">
        <div
          className={`flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-xl border ${avatarColors[index % avatarColors.length]}`}
        >
          {character.characterImage ? (
            <img
              src={character.characterImage}
              alt={character.characterName}
              className="size-14 object-cover"
            />
          ) : (
            <span className="text-3xl">🍁</span>
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
          <div className="flex items-center justify-between gap-2">
            <h2 className="truncate text-[20px] font-bold leading-[1.25]">
              {character.characterName}
            </h2>
            <img src={assets.imgStar} alt="즐겨찾기" />
          </div>
          <p className="truncate text-[12px] leading-[1.45]">
            {[
              character.characterLevel !== null ? `Lv.${character.characterLevel}` : null,
              character.characterClass,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
          <div className="flex items-center gap-1">
            {world && <img src={world[1]} alt="" className="size-[14px] object-contain" />}
            <p className="text-[11px] leading-[1.45] text-[var(--dash-muted)]">
              {character.worldName}
            </p>
          </div>
        </div>
      </div>
      <div className="h-px bg-[var(--dash-border)]" />
      {character.hasRecords === false ? (
        <p className="py-8 text-center text-[13px] text-[var(--dash-muted)]">기록 없음</p>
      ) : (
        <div className="flex flex-col gap-[17px]">
          {progressItems.map(({ key, label }) => {
            const { completed, total } = character[key]
            return (
              <div key={key} className="flex items-center gap-3">
                <span className="w-12 shrink-0 text-[13px] leading-[1.45] text-[var(--dash-muted)]">
                  {label}
                </span>
                <div
                  role="progressbar"
                  aria-label={label}
                  aria-valuenow={completed}
                  aria-valuemin={0}
                  aria-valuemax={total || 1}
                  className="h-[7px] min-w-0 flex-1 overflow-hidden rounded-full bg-[var(--dash-track)]"
                >
                  <div
                    style={{
                      width: `${total > 0 ? Math.min(100, (completed / total) * 100) : 0}%`,
                    }}
                    className="h-full rounded-full bg-[linear-gradient(90deg,var(--dash-accent),#11b7d2)]"
                  />
                </div>
                <div className="w-[50px] shrink-0 text-right text-[12px] leading-[1.4] [font-family:Inter,sans-serif]">
                  <p>
                    {completed}/{total}
                  </p>
                  {total === 0 && <p className="text-[11px] text-[var(--dash-muted)]">비대상</p>}
                </div>
              </div>
            )
          })}
        </div>
      )}
      <Link
        to={`/dashboard/${character.characterId}`}
        className="absolute right-[23px] bottom-[9px] flex min-h-11 min-w-[104px] items-center justify-end gap-[6px] rounded-lg text-[12px] leading-[14px] text-[var(--dash-accent)] focus-visible:outline-2 focus-visible:outline-[var(--dash-accent)]"
      >
        상세 보기
        <img src={assets.imgArrowUpRight} alt="" />
      </Link>
    </article>
  )
}
