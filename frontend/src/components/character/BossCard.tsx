import DifficultyBadge from '../common/DifficultyBadge'
import type { BossMaster, SchedulerBossRecord } from '../../types'

interface Props {
  boss: BossMaster
  record: SchedulerBossRecord
  onToggle: () => void
  onClickDetail: () => void
}

export default function BossCard({ boss, record, onToggle, onClickDetail }: Props) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClickDetail}
      onKeyDown={(e) => { if (e.key === 'Enter') onClickDetail() }}
      className="cursor-pointer rounded-lg bg-[#1a1a2e] p-4 transition-colors hover:bg-[#252540] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#4ade80]"
    >
      <div className="mb-3 flex items-start gap-3">
        {boss.bossImage ? (
          <img src={boss.bossImage} alt={boss.bossName} className="h-12 w-12 rounded object-contain" />
        ) : (
          <div className="flex h-12 w-12 items-center justify-center rounded bg-[#2d2d44] text-2xl">
            🗡️
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-white">{boss.bossName}</p>
          <DifficultyBadge difficulty={boss.difficulty} />
        </div>
      </div>

      <div className="flex items-center justify-between">
        <span className="text-xs text-white/40">
          {boss.crystalPrice > 0 ? `${boss.crystalPrice.toLocaleString()} 메소` : '무결정'}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onToggle()
          }}
          className={`rounded px-2 py-1 text-xs font-semibold transition-colors ${
            record.completed
              ? 'bg-[#4ade80]/20 text-[#4ade80]'
              : 'bg-white/10 text-white/50 hover:bg-white/20'
          }`}
        >
          {record.completed ? '✓ 처치' : '미처치'}
        </button>
      </div>
    </div>
  )
}
