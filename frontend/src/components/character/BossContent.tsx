import { useCallback, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import BossCard from './BossCard'
import BossDropModal from './BossDropModal'
import ThemedConfirmDialog from '../common/ThemedConfirmDialog'
import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from './assets'
import { controlButton, countPill, detailCard } from './styles'
import BossSelectionModal, { BOSS_LIMITS, clearedCount, countLabel, type BossSelectionChange, type MonthlyBlock } from './BossSelectionModal'
import { addBossesToPeriod, hideBossFromPeriod, saveBossPeriod, useCharacterBoss } from '../../hooks/useCharacterDetail'
import { monthLabel, weekMonths } from '../../utils/date'
import type { BossPeriod, BossPeriodDraft, SchedulerBossRecord } from '../../types'

interface Props {
  characterId: number
  date: string
  weekStart: string
}

// 월간 기록에 조회한 월 시작일을 붙여 기간 키로 사용
type BossRecord = SchedulerBossRecord & { monthStart?: string }

function BossGroup({
  title,
  counts,
  records,
  onClickDetail,
  onDelete,
}: {
  title: string
  counts: string[]
  records: BossRecord[]
  onClickDetail: (record: BossRecord) => void
  onDelete: (record: BossRecord) => (() => void) | undefined
}) {
  return (
    <>
      <div className="flex items-center gap-[10px]">
        <h3 className="text-[15px] font-bold">{title}</h3>
        {counts.map((count) => <span key={count} className={countPill}>{count}</span>)}
      </div>
      {records.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {records.map((record, idx) => (
            <BossCard
              key={record.id ?? `${record.characterId}-${record.bossId}-${idx}`}
              record={record}
              onClickDetail={() => onClickDetail(record)}
              onDelete={onDelete(record)}
              badge={record.monthStart && monthLabel(record.monthStart)}
            />
          ))}
        </div>
      )}
    </>
  )
}

export default function BossContent({ characterId, date, weekStart: selectedWeekStart }: Props) {
  const queryClient = useQueryClient()
  const months = weekMonths(selectedWeekStart, date)
  const first = useCharacterBoss(characterId, selectedWeekStart, months[0])
  const second = useCharacterBoss(characterId, selectedWeekStart, months[1] ?? months[0])
  const queries = months.length > 1 ? [first, second] : [first]
  const isLoading = queries.some((query) => query.isLoading)
  const isError = queries.some((query) => query.isError)
  const [selectedRecord, setSelectedRecord] = useState<BossRecord | null>(null)
  const [shownWeekStart, setShownWeekStart] = useState(selectedWeekStart)
  if (shownWeekStart !== selectedWeekStart) {
    setShownWeekStart(selectedWeekStart)
    setSelectedRecord(null)
  }
  const [selecting, setSelecting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<BossRecord | null>(null)
  const [deleting, setDeleting] = useState(false)
  const assets = characterAssets[useThemeStore().theme]
  const [drafts, setDrafts] = useState<Record<string, BossPeriodDraft>>({})
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const periodStart = (record: BossRecord) => record.resetPeriod === 'MONTHLY'
    ? record.monthStart ?? months[0]
    : selectedWeekStart
  const keyFor = (bossId: number, start: string) => `${characterId}:${bossId}:${start}`
  const selectedStart = selectedRecord ? periodStart(selectedRecord) : ''
  const selectedKey = selectedRecord ? keyFor(selectedRecord.bossId ?? 0, selectedStart) : ''
  const dirtyDrafts = Object.values(drafts).filter((draft) => draft.dirty && draft.characterId === characterId)

  const handleLoaded = useCallback((period: BossPeriod) => {
    const key = `${period.characterId}:${period.bossId}:${period.periodStart}`
    setDrafts((previous) => previous[key] ? previous : {
      ...previous,
      [key]: { ...period, dirty: false },
    })
  }, [setDrafts])

  useEffect(() => {
    if (dirtyDrafts.length === 0) return
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirtyDrafts.length])

  const handleSave = async () => {
    if (saving || dirtyDrafts.length === 0) return
    setSaving(true)
    setSaveError(false)
    setSaveSuccess(false)
    try {
      for (const draft of dirtyDrafts) {
        const saved = await saveBossPeriod(draft)
        const key = keyFor(draft.bossId, draft.periodStart)
        queryClient.setQueryData(['boss-period', characterId, draft.bossId, draft.periodStart], saved)
        setDrafts((previous) => previous[key] === draft
          ? { ...previous, [key]: { ...saved, dirty: false } }
          : previous)
      }
      await queryClient.invalidateQueries({
        predicate: (query) => typeof query.queryKey[0] === 'string' && query.queryKey[0].startsWith('stats/'),
      })
      setSaveSuccess(true)
    } catch {
      setSaveError(true)
    } finally {
      setSaving(false)
    }
  }

  const weeklyRecords: BossRecord[] = first.data?.weeklyBosses ?? []
  const weeklyCustomizable = first.data?.weeklyCustomizable === true
  const monthBlocks: MonthlyBlock[] = months.map((start, index) => {
    const data = queries[index]?.data
    return {
      start,
      records: (data?.monthlyBosses ?? []).map((record) => ({ ...record, monthStart: start })),
      customizable: data?.monthlyCustomizable === true,
    }
  })
  const monthlyRecords: BossRecord[] = monthBlocks.flatMap((block) => block.records)
  const canCustomize = (record: BossRecord) => record.resetPeriod === 'MONTHLY'
    ? monthBlocks.some((block) => block.start === record.monthStart && block.customizable)
    : weeklyCustomizable
  const anyCustomizable = weeklyCustomizable || monthBlocks.some((block) => block.customizable)

  const clearPeriodDraft = (record: BossRecord) => {
    if (!record.bossId) return
    const draftKey = keyFor(record.bossId, periodStart(record))
    setDrafts((previous) => {
      const next = { ...previous }
      delete next[draftKey]
      return next
    })
  }

  const refreshBossQueries = async () => {
    await queryClient.invalidateQueries({ queryKey: ['scheduler/boss', characterId, selectedWeekStart] })
    await queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
    await queryClient.invalidateQueries({ predicate: (query) => String(query.queryKey[0]).startsWith('stats/') })
  }

  const saveSelection = async ({ add, remove }: BossSelectionChange) => {
    try {
      for (const record of remove) {
        if (!record.bossId) continue
        await hideBossFromPeriod(characterId, record.bossId, periodStart(record), record.resetPeriod)
        clearPeriodDraft(record)
      }
      const additions = [
        { periodStart: selectedWeekStart, resetPeriod: 'WEEKLY' as const, bossIds: add.WEEKLY },
        ...Object.entries(add.MONTHLY).map(([start, bossIds]) => ({ periodStart: start, resetPeriod: 'MONTHLY' as const, bossIds })),
      ]
      for (const addition of additions) {
        if (addition.bossIds.length > 0) await addBossesToPeriod(characterId, addition)
      }
    } finally {
      await refreshBossQueries()
    }
  }

  const deleteBoss = async (record: BossRecord) => {
    if (!record.bossId) return
    setDeleteError(null)
    setDeleting(true)
    const start = periodStart(record)
    try {
      await hideBossFromPeriod(characterId, record.bossId, start, record.resetPeriod)
      clearPeriodDraft(record)
      setSelectedRecord(null)
      await refreshBossQueries()
    } catch {
      setDeleteError('보스 삭제에 실패했습니다.')
    } finally {
      setDeleting(false)
      setPendingDelete(null)
    }
  }

  return (
    <section className={`${detailCard} flex flex-col gap-5 p-6`}>
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-[18px] font-bold">보스 컨텐츠</h2>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => setSelecting(true)}
            disabled={isLoading || isError || !anyCustomizable}
            title={!anyCustomizable ? '최근 3주와 그 주가 포함된 달은 넥슨 스케줄러 기록으로 표시됩니다' : undefined}
            className={`${controlButton} disabled:cursor-not-allowed disabled:opacity-40`}>
            <img src={assets.imgManage} alt="" className="size-4" />
            보스 선택
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={dirtyDrafts.length === 0 || saving}
            className="flex h-10 shrink-0 items-center gap-2 rounded-[10px] bg-[var(--dash-accent)] px-4 text-[14px] font-bold text-[var(--dash-button-text)] disabled:cursor-not-allowed"
          >
            <img src={assets.imgSave} alt="" className="size-4" />
            {saving ? '저장 중' : '저장'}
          </button>
        </div>
      </div>
      {saveError && <p role="alert" className="text-[13px] text-[var(--dash-danger)]">저장에 실패했습니다.</p>}
      {deleteError && <p role="alert" className="text-[13px] text-[var(--dash-danger)]">{deleteError}</p>}
      {saveSuccess && !saveError && <p role="status" className="text-[13px] text-[var(--dash-success)]">저장 완료</p>}

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-[118px] animate-pulse rounded-[14px] bg-[var(--dash-subtle)]" />
          ))}
        </div>
      ) : isError ? (
        <p className="text-[14px] text-[var(--dash-danger)]">불러오는 중 오류가 발생했습니다.</p>
      ) : (
        <>
          <BossGroup
            title="주간 보스"
            counts={[countLabel(clearedCount(weeklyRecords.map((record) => record.isCompleted)), BOSS_LIMITS.WEEKLY)]}
            records={weeklyRecords}
            onClickDetail={setSelectedRecord}
            onDelete={(record) => canCustomize(record) ? () => setPendingDelete(record) : undefined}
          />
          <BossGroup
            title="월간 보스"
            counts={monthBlocks.map((block) =>
              countLabel(clearedCount(block.records.map((record) => record.isCompleted)), BOSS_LIMITS.MONTHLY, block.start))}
            records={monthlyRecords}
            onClickDetail={setSelectedRecord}
            onDelete={(record) => canCustomize(record) ? () => setPendingDelete(record) : undefined}
          />
        </>
      )}

      {selecting && (
        <BossSelectionModal
          characterId={characterId}
          weeklyRecords={weeklyRecords}
          weeklyCustomizable={weeklyCustomizable}
          months={monthBlocks}
          onClose={() => setSelecting(false)}
          onSave={saveSelection}
        />
      )}

      {selectedRecord && (
        <BossDropModal
          key={selectedKey}
          onClose={() => setSelectedRecord(null)}
          record={selectedRecord}
          characterId={characterId}
          periodStart={selectedStart}
          draft={drafts[selectedKey]}
          onLoaded={handleLoaded}
          onChange={(draft) => {
            setDrafts((previous) => ({ ...previous, [selectedKey]: draft }))
            setSaveSuccess(false)
          }}
        />
      )}
      {pendingDelete && (
        <ThemedConfirmDialog
          title="보스 삭제"
          confirmLabel="삭제"
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void deleteBoss(pendingDelete)}
        >
          {pendingDelete.bossName} 보스를 이 기간에서 삭제하시겠습니까? 해당 기간의 아이템·결정석 획득 내역도 삭제됩니다.
        </ThemedConfirmDialog>
      )}
    </section>
  )
}
