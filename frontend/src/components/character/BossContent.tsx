import { useCallback, useEffect, useState } from 'react'
import { Save } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import BossCard from './BossCard'
import BossDropModal from './BossDropModal'
import { saveBossPeriod, useCharacterBoss } from '../../hooks/useCharacterDetail'
import { thursdayWeekStart } from '../../utils/date'
import type { BossPeriod, BossPeriodDraft, SchedulerBossRecord } from '../../types'

interface Props {
  characterId: number
  date: string
}

function BossGroup({
  title,
  records,
  onClickDetail,
}: {
  title: string
  records: SchedulerBossRecord[]
  onClickDetail: (record: SchedulerBossRecord) => void
}) {
  if (records.length === 0) return null

  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold text-white/60">{title}</h3>
      <div className="grid grid-cols-2 gap-3">
        {records.map((record, idx) => (
          <BossCard
            key={record.id ?? `${record.characterId}-${idx}`}
            record={record}
            onClickDetail={() => onClickDetail(record)}
          />
        ))}
      </div>
    </div>
  )
}

export default function BossContent({ characterId, date }: Props) {
  const queryClient = useQueryClient()
  const { data: bossRecords, isLoading, isError } = useCharacterBoss(characterId, date)
  const [selectedRecord, setSelectedRecord] = useState<SchedulerBossRecord | null>(null)
  const [drafts, setDrafts] = useState<Record<string, BossPeriodDraft>>({})
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const periodStart = (record: SchedulerBossRecord) => record.resetPeriod === 'MONTHLY'
    ? `${date.slice(0, 7)}-01`
    : thursdayWeekStart(new Date(`${date}T12:00:00`))
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

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold text-white">보스 컨텐츠</h2>
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
      {saveError && <p role="alert" className="mb-3 text-sm text-[#f87171]">저장에 실패했습니다.</p>}
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
          />
          <BossGroup
            title="월간 보스"
            records={monthlyRecords}
            onClickDetail={setSelectedRecord}
          />
          {weeklyRecords.length === 0 && monthlyRecords.length === 0 && (
            <p className="text-sm text-white/40">보스 기록 없음</p>
          )}
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
