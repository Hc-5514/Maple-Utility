import { useRef } from 'react'
import { Link } from 'react-router-dom'
import GlassSelect from '../common/GlassSelect'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from './assets'
import { formatDotDate } from './format'
import type { Character } from '../../types'

function DateButton({
  label,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  value: string
  min?: string
  max?: string
  onChange: (value: string) => void
}) {
  const { theme } = useThemeStore()
  const input = useRef<HTMLInputElement>(null)
  return (
    <div className="relative">
      <button
        type="button"
        aria-label={`${label} ${value}`}
        onClick={() => {
          if (typeof input.current?.showPicker === 'function') input.current.showPicker()
          else input.current?.focus()
        }}
        className="flex h-11 items-center gap-2 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-[18px] text-[14px] font-bold whitespace-nowrap"
      >
        <img src={huntingAssets[theme].calendar} alt="" />
        {formatDotDate(value)}
      </button>
      <input
        ref={input}
        type="date"
        tabIndex={-1}
        aria-label={label}
        value={value}
        min={min}
        max={max}
        onChange={(event) => event.target.value && onChange(event.target.value)}
        className="pointer-events-none absolute inset-0 opacity-0"
      />
    </div>
  )
}

export default function HuntingFilters({
  dateFrom,
  dateTo,
  today,
  characterId,
  characters,
  onDateChange,
  onCharacterChange,
}: {
  dateFrom: string
  dateTo: string
  today: string
  characterId: number | null
  characters: Character[]
  onDateChange: (from: string, to: string) => void
  onCharacterChange: (characterId: number | null) => void
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
      <Link
        to="/hunting/new"
        className="flex h-11 items-center gap-2 rounded-[10px] border border-white/15 bg-[var(--dash-primary)] px-[18px] text-[14px] font-bold text-white"
      >
        <img src={huntingAssets[theme].plus} alt="" />
        기록 추가
      </Link>
    </div>
  )
}
