import BossCard from './BossCard'
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
}: {
  title: string
  records: SchedulerBossRecord[]
  bossMasterMap: Map<number, BossMaster>
  onToggle: (record: SchedulerBossRecord) => void
}) {
  if (records.length === 0) return null

  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold text-white/60">{title}</h3>
      <div className="grid grid-cols-2 gap-3">
        {records.map((record) => {
          const boss = bossMasterMap.get(record.bossId)
          if (!boss) return null
          return (
            <BossCard
              key={record.id}
              boss={boss}
              record={record}
              onToggle={() => onToggle(record)}
              onClickDetail={() => {}}
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

  const isLoading = loadingRecords || loadingMasters
  const bossMasterMap = new Map<number, BossMaster>((bossMasters ?? []).map((b) => [b.id, b]))

  const weeklyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'WEEKLY')
  const monthlyRecords = (bossRecords ?? []).filter((r) => r.resetPeriod === 'MONTHLY')

  const handleToggle = (record: SchedulerBossRecord) => {
    toggleBoss.mutate({ id: record.id, isCompleted: !record.isCompleted })
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
          />
          <BossGroup
            title="월간 보스"
            records={monthlyRecords}
            bossMasterMap={bossMasterMap}
            onToggle={handleToggle}
          />
        </div>
      )}
    </section>
  )
}
