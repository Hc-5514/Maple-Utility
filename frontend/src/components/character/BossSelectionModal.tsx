import { useState } from 'react'
import ThemedDialog, { dialogCancel, dialogConfirm } from '../common/ThemedDialog'
import ThemedCheckbox from '../common/ThemedCheckbox'
import ThemedConfirmDialog from '../common/ThemedConfirmDialog'
import { DIFFICULTY_COLOR } from '../common/DifficultyBadge'
import { useBossCandidates } from '../../hooks/useCharacterDetail'
import { countPill } from './styles'
import { monthLabel } from '../../utils/date'
import type { BossCandidate, ResetPeriod, SchedulerBossRecord } from '../../types'

export const BOSS_LIMITS: Record<ResetPeriod, number> = { WEEKLY: 12, MONTHLY: 1 }

const LOCKED_NOTICE: Record<ResetPeriod, string> = {
  WEEKLY: '최근 3주는 넥슨 스케줄러 기록으로 표시되어 변경할 수 없습니다',
  MONTHLY: '최근 3주가 포함된 달은 넥슨 스케줄러 기록으로 표시되어 변경할 수 없습니다',
}

/** n/limit counts cleared bosses (the game caps clears at the limit), not bosses listed in the scheduler. */
export function clearedCount(clearedFlags: boolean[]) {
  return clearedFlags.filter(Boolean).length
}

export function countLabel(cleared: number, limit: number, month?: string) {
  return `${month ? `${monthLabel(month)} ` : ''}${cleared} / ${limit}`
}

/** 선택 주가 걸친 한 달의 월간 보스 */
export interface MonthlyBlock {
  start: string
  records: SchedulerBossRecord[]
  customizable: boolean
}

export interface BossSelectionChange {
  add: { WEEKLY: number[]; MONTHLY: Record<string, number[]> }
  remove: SchedulerBossRecord[]
}

interface Props {
  characterId: number
  weeklyRecords: SchedulerBossRecord[]
  weeklyCustomizable: boolean
  months: MonthlyBlock[]
  onClose: () => void
  onSave: (change: BossSelectionChange) => Promise<void>
}

// 선택 상태 키: 주간은 'WEEKLY', 월간은 해당 월 시작일
type SelectionKey = string

function selectedIds(records: SchedulerBossRecord[]) {
  return records.flatMap((record) => (record.bossId ? [record.bossId] : []))
}

function BossOption({ boss, checked, disabled, onToggle }: {
  boss: BossCandidate
  checked: boolean
  disabled: boolean
  onToggle: () => void
}) {
  return (
    <label
      className={`flex h-11 items-center gap-[10px] rounded-[10px] px-[14px] ${checked ? 'border-[1.5px] border-[var(--dash-accent)] bg-[var(--dash-selected)]' : 'border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)]'} ${disabled ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'}`}
    >
      <ThemedCheckbox size={18} checked={checked} disabled={disabled} onChange={onToggle} label={`${boss.bossName} ${boss.difficulty}`} />
      <span className={`truncate text-[14px] ${checked ? 'font-bold text-[var(--dash-accent)]' : ''}`}>{boss.bossName}</span>
      <span className="shrink-0 text-[12px] font-semibold [font-family:Inter,sans-serif]" style={{ color: DIFFICULTY_COLOR[boss.difficulty] }}>
        {boss.difficulty}
      </span>
    </label>
  )
}

export default function BossSelectionModal({ characterId, weeklyRecords, weeklyCustomizable, months, onClose, onSave }: Props) {
  const weekly = useBossCandidates(characterId, 'WEEKLY')
  const monthly = useBossCandidates(characterId, 'MONTHLY')
  const sources: Record<SelectionKey, { period: ResetPeriod; records: SchedulerBossRecord[]; customizable: boolean }> = {
    WEEKLY: { period: 'WEEKLY', records: weeklyRecords, customizable: weeklyCustomizable },
    ...Object.fromEntries(months.map((month) => [month.start, { period: 'MONTHLY' as const, records: month.records, customizable: month.customizable }])),
  }
  const [selected, setSelected] = useState<Record<SelectionKey, number[]>>(() =>
    Object.fromEntries(Object.entries(sources).map(([key, source]) => [key, selectedIds(source.records)])))
  const [monthTab, setMonthTab] = useState(months[0]?.start ?? '')
  const [confirming, setConfirming] = useState<SchedulerBossRecord[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(false)

  // Newly selected custom bosses are recorded as cleared.
  const recordFor = (key: SelectionKey, bossId: number) => sources[key].records.find((record) => record.bossId === bossId)

  const candidatesFor = (period: ResetPeriod) => (period === 'WEEKLY' ? weekly.data : monthly.data) ?? []

  const toggle = (key: SelectionKey, bossId: number) => {
    setSelected((previous) => ({
      ...previous,
      [key]: previous[key].includes(bossId)
        ? previous[key].filter((id) => id !== bossId)
        : [...previous[key], bossId],
    }))
  }

  const isDisabled = (key: SelectionKey, boss: BossCandidate) => {
    const { period, customizable } = sources[key]
    if (!customizable) return true
    const ids = selected[key]
    if (ids.includes(boss.id)) return false
    if (ids.length >= BOSS_LIMITS[period]) return true
    return candidatesFor(period).some((other) => ids.includes(other.id) && other.bossName === boss.bossName)
  }

  const added = (key: SelectionKey) => selected[key].filter((id) => !selectedIds(sources[key].records).includes(id))

  const change = (): BossSelectionChange => ({
    add: {
      WEEKLY: added('WEEKLY'),
      MONTHLY: Object.fromEntries(months.map((month) => [month.start, added(month.start)])),
    },
    remove: Object.entries(sources).flatMap(([key, source]) =>
      source.records.filter((record) => record.bossId && !selected[key].includes(record.bossId))),
  })

  const save = async (confirmed = false) => {
    const next = change()
    if (!confirmed && next.remove.length > 0) {
      setConfirming(next.remove)
      return
    }
    setSaving(true)
    setError(false)
    try {
      await onSave(next)
      onClose()
    } catch {
      setError(true)
    } finally {
      setSaving(false)
      setConfirming(null)
    }
  }

  const count = (key: SelectionKey) => {
    const { period } = sources[key]
    const cleared = clearedCount(selected[key].map((id) => recordFor(key, id)?.isCompleted ?? true))
    return countLabel(cleared, BOSS_LIMITS[period], period === 'MONTHLY' ? key : undefined)
  }

  const notice = (key: SelectionKey) => {
    const { period, customizable } = sources[key]
    if (!customizable) return <span className="ml-auto text-[12px] text-[var(--dash-muted)]">{LOCKED_NOTICE[period]}</span>
    if (period === 'WEEKLY' && selected[key].length >= BOSS_LIMITS[period])
      return <span className="ml-auto text-[12px] text-[var(--dash-muted)]">최대 개수를 모두 선택했습니다</span>
    return null
  }

  const sectionHeader = (key: SelectionKey, title: string) => (
    <div className="flex items-center gap-[10px]">
      <h3 className="text-[16px] font-bold">{title}</h3>
      {key !== 'WEEKLY' && months.length > 1 && (
        <div role="tablist" aria-label="월간 보스 대상 월" className="flex gap-1 rounded-[10px] border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)] p-[3px]">
          {months.map((month) => (
            <button
              key={month.start}
              type="button"
              role="tab"
              aria-selected={month.start === key}
              onClick={() => setMonthTab(month.start)}
              className={`rounded-lg px-3 py-[5px] text-[13px] ${month.start === key ? 'bg-[var(--dash-card)] font-bold text-[var(--dash-accent)] shadow-[0_1px_3px_rgba(0,0,0,0.08)]' : 'text-[var(--dash-muted)]'}`}
            >
              {monthLabel(month.start)}
            </button>
          ))}
        </div>
      )}
      <span className={countPill}>{count(key)}</span>
      {notice(key)}
    </div>
  )

  const options = (key: SelectionKey) => (
    <div className="grid grid-cols-1 gap-[10px] sm:grid-cols-3">
      {candidatesFor(sources[key].period).map((boss) => (
        <BossOption
          key={boss.id}
          boss={boss}
          checked={selected[key].includes(boss.id)}
          disabled={saving || isDisabled(key, boss)}
          onToggle={() => toggle(key, boss.id)}
        />
      ))}
    </div>
  )

  return (
    <>
      <ThemedDialog
        title="보스 선택"
        description={`보스당 1개 난이도만 선택할 수 있습니다. 주간 보스는 최대 ${BOSS_LIMITS.WEEKLY}개, 월간 보스는 최대 ${BOSS_LIMITS.MONTHLY}개까지 추가할 수 있습니다.`}
        width={944}
        top={120}
        busy={saving || confirming !== null}
        onClose={onClose}
        actions={
          <>
            <button type="button" disabled={saving} onClick={onClose} className={dialogCancel}>
              취소
            </button>
            <button type="button" disabled={saving} onClick={() => void save()} className={dialogConfirm}>
              {saving ? '저장 중' : '저장'}
            </button>
          </>
        }
      >
        {sectionHeader('WEEKLY', '주간 보스')}
        {weekly.isLoading ? (
          <div className="h-[476px] animate-pulse rounded-xl bg-[var(--dash-subtle)]" />
        ) : (
          <div className="dialog-scroll max-h-[476px] overflow-y-auto pr-2">{options('WEEKLY')}</div>
        )}
        {monthTab && sectionHeader(monthTab, '월간 보스')}
        {monthTab && (monthly.isLoading ? <div className="h-11 animate-pulse rounded-xl bg-[var(--dash-subtle)]" /> : <div className="pr-[14px]">{options(monthTab)}</div>)}
        {(error || weekly.isError || monthly.isError) && (
          <p role="alert" className="text-[13px] text-[var(--dash-danger)]">
            {error ? '보스 선택을 저장하지 못했습니다.' : '보스 후보를 불러오지 못했습니다.'}
          </p>
        )}
      </ThemedDialog>
      {confirming && (
        <ThemedConfirmDialog
          title="보스 삭제"
          confirmLabel="삭제 후 저장"
          busy={saving}
          onCancel={() => setConfirming(null)}
          onConfirm={() => void save(true)}
        >
          {confirming.map((record) => `${record.bossName} ${record.difficulty ?? ''}`.trim()).join(', ')} 선택을 해제하면 해당 기간의 아이템·결정석 획득 내역도 삭제됩니다.
        </ThemedConfirmDialog>
      )}
    </>
  )
}
