import { useState } from 'react'
import BossCard from './BossCard'
import BossDropModal from './BossDropModal'
import { useCharacterBoss } from '../../hooks/useCharacterDetail'
import type { SchedulerBossRecord } from '../../types'

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
  const { data: bossRecords, isLoading, isError } = useCharacterBoss(characterId, date)
  const [selectedRecord, setSelectedRecord] = useState<SchedulerBossRecord | null>(null)

  const weeklyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'WEEKLY')
  const monthlyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'MONTHLY')

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <h2 className="mb-4 font-semibold text-white">보스 컨텐츠</h2>

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
        />
      )}
    </section>
  )
}
