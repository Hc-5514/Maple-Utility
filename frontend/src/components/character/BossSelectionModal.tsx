import { useState } from 'react'
import ThemedDialog, { dialogCancel, dialogConfirm } from '../common/ThemedDialog'
import ThemedCheckbox from '../common/ThemedCheckbox'
import ThemedConfirmDialog from '../common/ThemedConfirmDialog'
import { DIFFICULTY_COLOR } from '../common/DifficultyBadge'
import { useBossCandidates } from '../../hooks/useCharacterDetail'
import { countPill } from './styles'
import type { BossCandidate, ResetPeriod, SchedulerBossRecord } from '../../types'

export const BOSS_LIMITS: Record<ResetPeriod, number> = { WEEKLY: 12, MONTHLY: 1 }

const LOCKED_NOTICE: Record<ResetPeriod, string> = {
  WEEKLY: '최근 3주는 넥슨 스케줄러 기록으로 표시되어 변경할 수 없습니다',
  MONTHLY: '이번 달은 넥슨 스케줄러 기록으로 표시되어 변경할 수 없습니다',
}

/** n/limit means bosses cleared out of the clearable maximum, independent of how many the scheduler lists. */
export function clearedCount(clearedFlags: boolean[], limit: number) {
  return Math.min(clearedFlags.filter(Boolean).length, limit)
}

export interface BossSelectionChange {
  add: Record<ResetPeriod, number[]>
  remove: SchedulerBossRecord[]
}

interface Props {
  characterId: number
  weeklyRecords: SchedulerBossRecord[]
  monthlyRecords: SchedulerBossRecord[]
  customizable: Record<ResetPeriod, boolean>
  onClose: () => void
  onSave: (change: BossSelectionChange) => Promise<void>
}

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

export default function BossSelectionModal({ characterId, weeklyRecords, monthlyRecords, customizable, onClose, onSave }: Props) {
  const weekly = useBossCandidates(characterId, 'WEEKLY')
  const monthly = useBossCandidates(characterId, 'MONTHLY')
  const [selected, setSelected] = useState<Record<ResetPeriod, number[]>>({
    WEEKLY: selectedIds(weeklyRecords),
    MONTHLY: selectedIds(monthlyRecords),
  })
  const [confirming, setConfirming] = useState<SchedulerBossRecord[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(false)

  // Newly selected custom bosses are recorded as cleared.
  const recordFor = (period: ResetPeriod, bossId: number) =>
    (period === 'WEEKLY' ? weeklyRecords : monthlyRecords).find((record) => record.bossId === bossId)

  const candidatesFor = (period: ResetPeriod) => (period === 'WEEKLY' ? weekly.data : monthly.data) ?? []

  const toggle = (period: ResetPeriod, bossId: number) => {
    setSelected((previous) => ({
      ...previous,
      [period]: previous[period].includes(bossId)
        ? previous[period].filter((id) => id !== bossId)
        : [...previous[period], bossId],
    }))
  }

  const isDisabled = (period: ResetPeriod, boss: BossCandidate) => {
    if (!customizable[period]) return true
    const ids = selected[period]
    if (ids.includes(boss.id)) return false
    if (ids.length >= BOSS_LIMITS[period]) return true
    return candidatesFor(period).some((other) => ids.includes(other.id) && other.bossName === boss.bossName)
  }

  const change = (): BossSelectionChange => {
    const original = { WEEKLY: weeklyRecords, MONTHLY: monthlyRecords }
    return {
      add: {
        WEEKLY: selected.WEEKLY.filter((id) => !selectedIds(weeklyRecords).includes(id)),
        MONTHLY: selected.MONTHLY.filter((id) => !selectedIds(monthlyRecords).includes(id)),
      },
      remove: (['WEEKLY', 'MONTHLY'] as const).flatMap((period) =>
        original[period].filter((record) => record.bossId && !selected[period].includes(record.bossId))),
    }
  }

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

  const sectionHeader = (period: ResetPeriod, title: string) => (
    <div className="flex items-center gap-[10px]">
      <h3 className="text-[16px] font-bold">{title}</h3>
      <span className={countPill}>{clearedCount(selected[period].map((id) => recordFor(period, id)?.isCompleted ?? true), BOSS_LIMITS[period])}/{BOSS_LIMITS[period]}</span>
      {!customizable[period] ? (
        <span className="ml-auto text-[12px] text-[var(--dash-muted)]">{LOCKED_NOTICE[period]}</span>
      ) : period === 'WEEKLY' && selected[period].length >= BOSS_LIMITS[period] && (
        <span className="ml-auto text-[12px] text-[var(--dash-muted)]">최대 개수를 모두 선택했습니다</span>
      )}
    </div>
  )

  const options = (period: ResetPeriod) => (
    <div className="grid grid-cols-1 gap-[10px] sm:grid-cols-3">
      {candidatesFor(period).map((boss) => (
        <BossOption
          key={boss.id}
          boss={boss}
          checked={selected[period].includes(boss.id)}
          disabled={saving || isDisabled(period, boss)}
          onToggle={() => toggle(period, boss.id)}
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
        {sectionHeader('MONTHLY', '월간 보스')}
        {monthly.isLoading ? <div className="h-11 animate-pulse rounded-xl bg-[var(--dash-subtle)]" /> : <div className="pr-[14px]">{options('MONTHLY')}</div>}
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
