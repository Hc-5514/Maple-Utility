import type { KeyboardEvent } from 'react'
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
  // 드랍 아이템은 처치한 보스에만 존재하므로 미처치 카드는 상호작용 없음
  const detailProps = record.isCompleted
    ? {
        role: 'button',
        tabIndex: 0,
        'aria-label': `${record.bossName} 드랍 아이템`,
        onClick: onClickDetail,
        onKeyDown: (e: KeyboardEvent) => { if (e.key === 'Enter') onClickDetail() },
      }
    : {}
  return (
    <div className="relative">
      <div
        {...detailProps}
        className={`flex flex-col gap-[14px] rounded-[14px] px-5 py-[18px] ${
          record.isCompleted
            ? 'cursor-pointer transition-[filter] hover:brightness-105 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dash-accent)]'
            : ''
        } ${
          record.isCompleted
            ? 'border-[1.5px] border-[color-mix(in_srgb,var(--dash-success)_55%,transparent)] bg-[color-mix(in_srgb,var(--dash-success)_7%,transparent)]'
            : 'border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)]'
        }`}
      >
        <div className="flex items-center gap-[14px]">
          <CatalogImage src={record.bossImage} alt={record.bossName ?? '보스'} kind="boss" />
          <div className="flex min-w-0 flex-col items-start gap-[6px]">
            <p className="truncate text-[15px] font-bold">{record.bossName}</p>
            {record.difficulty && <DifficultyBadge difficulty={record.difficulty} />}
          </div>
          <div className="ml-auto flex shrink-0 items-center gap-[6px] self-start">
            {badge && <span className={countPill}>{badge}</span>}
            {record.isCompleted ? (
              <span className="flex items-center gap-1 rounded-full bg-[color-mix(in_srgb,var(--dash-success)_14%,transparent)] px-[10px] py-1 text-[12px] font-bold text-[var(--dash-success)]">
                <img src={assets.imgCheck} alt="" className="size-3" />
                처치
              </span>
            ) : (
              <span className="rounded-full bg-[color-mix(in_srgb,var(--dash-muted)_12%,transparent)] px-[10px] py-1 text-[12px] font-bold text-[var(--dash-muted)]">
                미처치
              </span>
            )}
          </div>
        </div>
        <div className={`flex min-h-[30px] items-center ${onDelete ? 'pr-[44px]' : ''}`}>
          <span className="text-[13px] text-[var(--dash-muted)]">
            {price > 0 ? `${price.toLocaleString()} 메소` : '가격 미확인'}
          </span>
        </div>
      </div>
      {onDelete && (
        <button
          type="button"
          onClick={onDelete}
          aria-label={`${record.bossName} 삭제`}
          title={`${record.bossName} 삭제`}
          className="absolute right-5 bottom-[18px] flex size-[30px] items-center justify-center rounded-lg hover:bg-[color-mix(in_srgb,var(--dash-danger)_14%,transparent)]"
        >
          <img src={assets.imgTrash} alt="" className="size-4" />
        </button>
      )}
    </div>
  )
}
