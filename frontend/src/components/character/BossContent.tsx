import { useState } from 'react'
import BossCard from './BossCard'
import BossDropModal from './BossDropModal'
import { useBossMasters, useCharacterBoss, useToggleBoss } from '../../hooks/useCharacterDetail'
import type { BossMaster, SchedulerBossRecord } from '../../types'

interface Props {
  characterId: number
  date: string
}

function BossGroup({
  title,
  records,
  bossMasterMap,
  onToggle,
  onClickDetail,
}: {
  title: string
  records: SchedulerBossRecord[]
  bossMasterMap: Map<number, BossMaster>
  onToggle: (record: SchedulerBossRecord) => void
  onClickDetail: (boss: BossMaster, record: SchedulerBossRecord) => void
}) {
  if (records.length === 0) return null

  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold text-white/60">{title}</h3>
      <div className="grid grid-cols-2 gap-3">
        {records.map((record, idx) => {
          const boss = bossMasterMap.get(record.bossId ?? 0)
          if (!boss) return null
          return (
            <BossCard
              key={record.id ?? `${record.characterId}-${idx}`}
              boss={boss}
              record={record}
              onToggle={() => onToggle(record)}
              onClickDetail={() => onClickDetail(boss, record)}
            />
          )
        })}
      </div>
    </div>
  )
}

export default function BossContent({ characterId, date }: Props) {
  const { data: bossRecords, isLoading: loadingRecords } = useCharacterBoss(characterId, date)
  const { data: bossMasters, isLoading: loadingMasters } = useBossMasters()
  const toggleBoss = useToggleBoss()

  const [selectedInfo, setSelectedInfo] = useState<{
    boss: BossMaster
    record: SchedulerBossRecord
  } | null>(null)

  const isLoading = loadingRecords || loadingMasters
  const bossMasterMap = new Map<number, BossMaster>((bossMasters ?? []).map((b) => [b.id, b]))

  const weeklyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'WEEKLY')
  const monthlyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'MONTHLY')

  const handleToggle = (record: SchedulerBossRecord) => {
    if (record.id == null) return
    toggleBoss.mutate({ id: record.id, completed: !record.completed })
  }

  return (
    <section className="rounded-xl bg-[#2d2d44] p-5">
      <h2 className="mb-4 font-semibold text-white">보스 컨텐츠</h2>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-lg bg-white/10" />
          ))}
        </div>
      ) : (
        <div className="space-y-5">
          <BossGroup
            title="주간 보스"
            records={weeklyRecords}
            bossMasterMap={bossMasterMap}
            onToggle={handleToggle}
            onClickDetail={(boss, record) => setSelectedInfo({ boss, record })}
          />
          <BossGroup
            title="월간 보스"
            records={monthlyRecords}
            bossMasterMap={bossMasterMap}
            onToggle={handleToggle}
            onClickDetail={(boss, record) => setSelectedInfo({ boss, record })}
          />
        </div>
      )}

      {selectedInfo && (
        <BossDropModal
          isOpen={true}
          onClose={() => setSelectedInfo(null)}
          boss={selectedInfo.boss}
          record={selectedInfo.record}
          characterId={characterId}
        />
      )}
    </section>
  )
}
