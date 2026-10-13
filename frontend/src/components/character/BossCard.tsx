import DifficultyBadge from '../common/DifficultyBadge'
import CatalogImage from '../common/CatalogImage'
import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from './assets'
import { countPill } from './styles'
import type { SchedulerBossRecord } from '../../types'

interface Props {
  record: SchedulerBossRecord
  onClickDetail: () => void
  onDelete?: () => void
  /** 월간 보스의 대상 월 표기 (예: 10월) */
  badge?: string
}

export default function BossCard({ record, onClickDetail, onDelete, badge }: Props) {
  const assets = characterAssets[useThemeStore().theme]
  const price = record.crystalPrice ?? 0
  return (
    <div className="relative">
      <div
        role="button"
        tabIndex={0}
        aria-label={`${record.bossName} 드랍 아이템`}
        onClick={onClickDetail}
        onKeyDown={(e) => { if (e.key === 'Enter') onClickDetail() }}
        className={`flex cursor-pointer flex-col gap-[14px] rounded-[14px] px-5 py-[18px] transition-[filter] hover:brightness-105 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dash-accent)] ${
          record.isCompleted
            ? 'border-[1.5px] border-[color-mix(in_srgb,var(--dash-success)_55%,transparent)] bg-[color-mix(in_srgb,var(--dash-success)_7%,transparent)]'
            : 'border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)]'
        }`}
      >
        <div className="flex items-center gap-[14px] pr-[44px]">
          <CatalogImage src={record.bossImage} alt={record.bossName ?? '보스'} kind="boss" />
          <div className="flex min-w-0 flex-col items-start gap-[6px]">
            <p className="truncate text-[15px] font-bold">{record.bossName}</p>
            {record.difficulty && <DifficultyBadge difficulty={record.difficulty} />}
          </div>
          {badge && <span className={`${countPill} ml-auto self-start`}>{badge}</span>}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[13px] text-[var(--dash-muted)]">
            {price > 0 ? `${price.toLocaleString()} 메소` : '가격 미확인'}
          </span>
          {record.isCompleted ? (
            <span className="ml-auto flex items-center gap-1 rounded-full bg-[color-mix(in_srgb,var(--dash-success)_14%,transparent)] px-[10px] py-1 text-[12px] font-bold text-[var(--dash-success)]">
              <img src={assets.imgCheck} alt="" className="size-3" />
              처치
            </span>
          ) : (
            <span className="ml-auto rounded-full bg-[color-mix(in_srgb,var(--dash-muted)_12%,transparent)] px-[10px] py-1 text-[12px] font-bold text-[var(--dash-muted)]">
              미처치
            </span>
          )}
        </div>
      </div>
      {onDelete && (
        <button
          type="button"
          onClick={onDelete}
          aria-label={`${record.bossName} 삭제`}
          title={`${record.bossName} 삭제`}
          className="absolute top-[27px] right-5 flex size-[30px] items-center justify-center rounded-lg hover:bg-[color-mix(in_srgb,var(--dash-danger)_14%,transparent)]"
        >
          <img src={assets.imgTrash} alt="" className="size-4" />
        </button>
      )}
    </div>
  )
}
