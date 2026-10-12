import { Link } from 'react-router-dom'
import DateButton from '../common/DateButton'
import GlassSelect from '../common/GlassSelect'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from './assets'
import type { Character } from '../../types'

export default function HuntingFilters({
  dateFrom,
  dateTo,
  today,
  characterId,
  characters,
  onDateChange,
  onCharacterChange,
  showAddButton = true,
}: {
  dateFrom: string
  dateTo: string
  today: string
  characterId: number | null
  characters: Character[]
  onDateChange: (from: string, to: string) => void
  onCharacterChange: (characterId: number | null) => void
  showAddButton?: boolean
}) {
  const { theme } = useThemeStore()
  return (
    <div className="relative z-20 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[19px] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px]">
      <div className="flex flex-wrap items-center gap-4">
        <span className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">기간</span>
        <DateButton
          label="시작일"
          value={dateFrom}
          max={dateTo}
          onChange={(value) => onDateChange(value, dateTo)}
        />
        <span className="text-[14px] text-[var(--dash-muted)]">~</span>
        <DateButton
          label="종료일"
          value={dateTo}
          min={dateFrom}
          max={today}
          onChange={(value) => onDateChange(dateFrom, value)}
        />
        <span aria-hidden="true" className="h-7 w-px bg-[var(--dash-border)]" />
        <GlassSelect
          label="캐릭터 필터"
          value={characterId}
          openStyle="tint"
          menuClassName="min-w-[183px]"
          options={[
            { value: null, label: '전체 즐겨찾기 캐릭터' },
            ...characters.map((character) => ({ value: character.id, label: character.characterName })),
          ]}
          onChange={onCharacterChange}
        />
      </div>
      {showAddButton && (
        <Link
          to="/hunting/new"
          className="flex h-11 items-center gap-2 rounded-[10px] border border-white/15 bg-[var(--dash-primary)] px-[18px] text-[14px] font-bold text-white"
        >
          <img src={huntingAssets[theme].plus} alt="" />
          기록 추가
        </Link>
      )}
    </div>
  )
}
