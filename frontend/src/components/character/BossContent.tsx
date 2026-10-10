import { useCallback, useEffect, useState } from 'react'
import { CalendarDays, Plus, Save, Trash2 } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import BossCard from './BossCard'
import BossDropModal from './BossDropModal'
import { addBossesToPeriod, hideBossFromPeriod, saveBossPeriod, useBossCandidates, useCharacterBoss } from '../../hooks/useCharacterDetail'
import { localDate, monthStart, thursdayWeekStart } from '../../utils/date'
import type { BossPeriod, BossPeriodDraft, ResetPeriod, SchedulerBossRecord } from '../../types'

interface Props {
  characterId: number
  date: string
}

function BossGroup({
  title,
  records,
  onClickDetail,
  onAdd,
  onDelete,
}: {
  title: string
  records: SchedulerBossRecord[]
  onClickDetail: (record: SchedulerBossRecord) => void
  onAdd: () => void
  onDelete: (record: SchedulerBossRecord) => void
}) {
  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white/60">{title}</h3>
        <button type="button" onClick={onAdd} aria-label={`${title} 추가`} title={`${title} 추가`} className="grid size-7 place-items-center rounded text-white/60 hover:bg-white/10 hover:text-white"><Plus size={16} /></button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {records.map((record, idx) => (
          <div key={record.id ?? `${record.characterId}-${record.bossId}-${idx}`} className="relative">
            <BossCard record={record} onClickDetail={() => onClickDetail(record)} />
            <button type="button" onClick={() => onDelete(record)} aria-label={`${record.bossName} 삭제`} title={`${record.bossName} 삭제`} className="absolute right-2 top-2 grid size-7 place-items-center rounded bg-[#1a1a2e] text-white/50 hover:bg-[#f87171]/20 hover:text-[#f87171]"><Trash2 size={15} /></button>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function BossContent({ characterId, date }: Props) {
  const queryClient = useQueryClient()
  const pageDate = new Date(`${date}T12:00:00`)
  const [selectedWeekStart, setSelectedWeekStart] = useState(() => thursdayWeekStart(pageDate))
  const [selectedMonthStart, setSelectedMonthStart] = useState(() => monthStart(pageDate))
  const { data: bossRecords, isLoading, isError } = useCharacterBoss(characterId, selectedWeekStart, selectedMonthStart)
  const [selectedRecord, setSelectedRecord] = useState<SchedulerBossRecord | null>(null)
  const [manualPeriod, setManualPeriod] = useState<ResetPeriod | null>(null)
  const [selectedBossIds, setSelectedBossIds] = useState<number[]>([])
  const [manualSaving, setManualSaving] = useState(false)
  const [manualError, setManualError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, BossPeriodDraft>>({})
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const { data: candidates = [], isLoading: candidatesLoading } = useBossCandidates(characterId, manualPeriod)
  const periodStart = (record: SchedulerBossRecord) => record.resetPeriod === 'MONTHLY'
    ? selectedMonthStart
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
  }, [])

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

  const weeklyRecords = bossRecords?.weeklyBosses ?? []
  const monthlyRecords = bossRecords?.monthlyBosses ?? []
  const weekOptions = Array.from({ length: 12 }, (_, index) => {
    const current = new Date(`${thursdayWeekStart(pageDate)}T12:00:00`)
    current.setDate(current.getDate() - index * 7)
    const start = localDate(current)
    const endDate = new Date(current)
    endDate.setDate(endDate.getDate() + 6)
    return { start, label: `${start.replace(/-/g, '.')} ~ ${localDate(endDate).replace(/-/g, '.')}` }
  })
  const monthOptions = Array.from({ length: 3 }, (_, index) => {
    const current = new Date(`${monthStart(pageDate)}T12:00:00`)
    current.setMonth(current.getMonth() - index)
    const start = monthStart(current)
    return { start, label: `${current.getFullYear()}년 ${current.getMonth() + 1}월` }
  })

  const openManualEditor = (resetPeriod: ResetPeriod) => {
    setManualPeriod(resetPeriod)
    setSelectedBossIds([])
    setManualError(null)
  }

  const toggleCandidate = (bossId: number) => {
    setSelectedBossIds((previous) => previous.includes(bossId)
      ? previous.filter((id) => id !== bossId)
      : [...previous, bossId])
  }

  const saveManual = async () => {
    if (!manualPeriod || selectedBossIds.length === 0 || manualSaving) return
    setManualSaving(true)
    setManualError(null)
    try {
      await addBossesToPeriod(characterId, {
        periodStart: manualPeriod === 'MONTHLY' ? selectedMonthStart : selectedWeekStart,
        resetPeriod: manualPeriod,
        bossIds: selectedBossIds,
      })
      await queryClient.invalidateQueries({ queryKey: ['scheduler/boss', characterId, selectedWeekStart, selectedMonthStart] })
      await queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
      setManualPeriod(null)
      setSelectedBossIds([])
    } catch {
      setManualError('보스 기록 저장 실패')
    } finally {
      setManualSaving(false)
    }
  }

  const deleteBoss = async (record: SchedulerBossRecord) => {
    if (!record.bossId) return
    if (!window.confirm(`${record.bossName} 보스를 이 기간에서 삭제하시겠습니까? 해당 기간의 아이템·결정석 획득 내역도 삭제됩니다.`)) return
    setDeleteError(null)
    const start = periodStart(record)
    try {
      await hideBossFromPeriod(characterId, record.bossId, start, record.resetPeriod)
      const draftKey = keyFor(record.bossId, start)
      setDrafts((previous) => {
        const next = { ...previous }
        delete next[draftKey]
        return next
      })
      setSelectedRecord(null)
      await queryClient.invalidateQueries({ queryKey: ['scheduler/boss', characterId, selectedWeekStart, selectedMonthStart] })
      await queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
      await queryClient.invalidateQueries({ predicate: (query) => String(query.queryKey[0]).startsWith('stats/') })
    } catch {
      setDeleteError('보스 삭제에 실패했습니다.')
    }
  }

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold text-white">보스 컨텐츠</h2>
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor="boss-week">보스 주차</label>
          <div className="relative">
            <CalendarDays size={15} className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-white/50" />
            <select
              id="boss-week"
              value={selectedWeekStart}
              onChange={(event) => {
                setSelectedWeekStart(event.target.value)
                setSelectedRecord(null)
                setManualPeriod(null)
              }}
              className="max-w-48 appearance-none rounded bg-[#1a1a2e] py-1.5 pl-7 pr-2 text-xs text-white"
            >
              {weekOptions.map((option) => <option key={option.start} value={option.start}>{option.label}</option>)}
            </select>
          </div>
          <label className="sr-only" htmlFor="boss-month">보스 월</label>
          <select
            id="boss-month"
            value={selectedMonthStart}
            onChange={(event) => {
              setSelectedMonthStart(event.target.value)
              setSelectedRecord(null)
              setManualPeriod(null)
            }}
            className="rounded bg-[#1a1a2e] px-2 py-1.5 text-xs text-white"
          >
            {monthOptions.map((option) => <option key={option.start} value={option.start}>{option.label}</option>)}
          </select>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={dirtyDrafts.length === 0 || saving}
            className="flex items-center gap-1.5 rounded bg-[#4ade80] px-3 py-1.5 text-sm font-medium text-[#102116] disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Save size={16} aria-hidden="true" />
            {saving ? '저장 중' : '저장'}
          </button>
        </div>
      </div>
      {saveError && <p role="alert" className="mb-3 text-sm text-[#f87171]">저장에 실패했습니다.</p>}
      {deleteError && <p role="alert" className="mb-3 text-sm text-[#f87171]">{deleteError}</p>}
      {saveSuccess && !saveError && <p role="status" className="mb-3 text-sm text-[#4ade80]">저장 완료</p>}

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-lg bg-white/10" />
          ))}
        </div>
      ) : isError ? (
        <p className="text-sm text-[#f87171]">불러오는 중 오류가 발생했습니다.</p>
      ) : (
        <div className="space-y-5">
          <BossGroup
            title="주간 보스"
            records={weeklyRecords}
            onClickDetail={setSelectedRecord}
            onAdd={() => openManualEditor('WEEKLY')}
            onDelete={(record) => void deleteBoss(record)}
          />
          <BossGroup
            title="월간 보스"
            records={monthlyRecords}
            onClickDetail={setSelectedRecord}
            onAdd={() => openManualEditor('MONTHLY')}
            onDelete={(record) => void deleteBoss(record)}
          />
          {weeklyRecords.length === 0 && monthlyRecords.length === 0 && (
            <p className="text-sm text-white/40">보스 기록 없음</p>
          )}
        </div>
      )}

      {manualPeriod && (
        <div className="mt-4 border-t border-white/10 pt-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="text-sm font-medium text-white">{manualPeriod === 'WEEKLY' ? '주간' : '월간'} 보스 선택</p>
            {manualPeriod === 'WEEKLY' && <span className="text-xs text-white/50">{weeklyRecords.length + selectedBossIds.length}/12</span>}
          </div>
          {candidatesLoading ? <p className="text-sm text-white/50">후보 불러오는 중</p> : (
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {candidates.filter((boss) => !(manualPeriod === 'WEEKLY' ? weeklyRecords : monthlyRecords).some((record) => record.bossId === boss.id)).map((boss) => {
                const checked = selectedBossIds.includes(boss.id)
                const disable = manualPeriod === 'WEEKLY' && !checked && weeklyRecords.length + selectedBossIds.length >= 12
                return (
                  <label key={boss.id} className={`flex cursor-pointer items-center gap-2 rounded border px-3 py-2 text-sm ${checked ? 'border-[#4ade80] bg-[#4ade80]/10 text-white' : 'border-white/10 text-white/70'} ${disable ? 'cursor-not-allowed opacity-40' : ''}`}>
                    <input type="checkbox" checked={checked} disabled={disable} onChange={() => toggleCandidate(boss.id)} />
                    <span className="truncate">{boss.bossName} {boss.difficulty}</span>
                  </label>
                )
              })}
            </div>
          )}
          {manualError && <p role="alert" className="mt-3 text-sm text-[#f87171]">{manualError}</p>}
          <div className="mt-4 flex justify-end gap-2">
            <button type="button" onClick={() => setManualPeriod(null)} className="rounded px-3 py-2 text-sm text-white/60 hover:text-white">취소</button>
            <button type="button" onClick={() => void saveManual()} disabled={selectedBossIds.length === 0 || manualSaving} className="rounded bg-[#4ade80] px-3 py-2 text-sm font-medium text-[#102116] disabled:opacity-40">
              {manualSaving ? '저장 중' : '선택 저장'}
            </button>
          </div>
        </div>
      )}

      {selectedRecord && (
        <BossDropModal
          isOpen={true}
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
    </section>
  )
}
