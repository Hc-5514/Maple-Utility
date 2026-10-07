import DifficultyBadge from '../common/DifficultyBadge'
import CatalogImage from '../common/CatalogImage'
import type { SchedulerBossRecord } from '../../types'

interface Props {
  record: SchedulerBossRecord
  onClickDetail: () => void
}

export default function BossCard({ record, onClickDetail }: Props) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClickDetail}
      onKeyDown={(e) => { if (e.key === 'Enter') onClickDetail() }}
      className="cursor-pointer rounded-lg bg-[#1a1a2e] p-4 transition-colors hover:bg-[#252540] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#4ade80]"
    >
      <div className="mb-3 flex items-start gap-3">
        <CatalogImage src={record.bossImage} alt={record.bossName ?? '보스'} kind="boss" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-white">{record.bossName}</p>
          {record.difficulty && <DifficultyBadge difficulty={record.difficulty} />}
        </div>
      </div>

      <div className="flex items-center justify-between">
        <span className="text-xs text-white/40">
          {(record.crystalPrice ?? 0) > 0
            ? `${(record.crystalPrice ?? 0).toLocaleString()} 메소`
            : '가격 미확인'}
        </span>
        <span
          className={`rounded px-2 py-1 text-xs font-semibold ${
            record.isCompleted
              ? 'bg-[#4ade80]/20 text-[#4ade80]'
              : 'bg-white/10 text-white/50'
          }`}
        >
          {record.isCompleted ? '✓ 처치' : '미처치'}
        </span>
      </div>
    </div>
  )
}
