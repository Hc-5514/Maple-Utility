import GlassSelect from '../common/GlassSelect'
import Pagination from '../common/Pagination'
import StatsDifficultyBadge from './StatsDifficultyBadge'
import type { BossItemPageSize } from '../../hooks/useStats'
import type { PageResponse, StatsBossItem } from '../../types'

const pageSizes: BossItemPageSize[] = [10, 20, 30]
const columns: [string, string][] = [
  ['기간', 'w-[240px]'],
  ['캐릭터', 'w-[150px]'],
  ['보스', 'w-[150px]'],
  ['난이도', 'w-[100px]'],
  ['아이템', 'min-w-[560px] flex-1'],
]

interface Props {
  page: PageResponse<StatsBossItem>
  bossName: string | null
  bosses: string[]
  luckyOnly: boolean
  onLuckyOnlyChange: (luckyOnly: boolean) => void
  onBossChange: (bossName: string | null) => void
  onPageChange: (page: number) => void
  onPageSizeChange: (size: BossItemPageSize) => void
}

export default function ItemAcquisitionList({
  page,
  bossName,
  bosses,
  luckyOnly,
  onLuckyOnlyChange,
  onBossChange,
  onPageChange,
  onPageSizeChange,
}: Props) {
  const cell = 'shrink-0 truncate text-[13px]'
  const status =
    page.totalElements === 0 ? (
      <p className="border-b border-[var(--dash-border)] py-16 text-center text-[13px] text-[var(--dash-muted)]">
        획득 기록이 없습니다
      </p>
    ) : page.content.length === 0 ? (
      <div className="border-b border-[var(--dash-border)] py-16 text-center text-[13px] text-[var(--dash-muted)]">
        <p>현재 페이지에 표시할 기록이 없습니다</p>
        <button type="button" onClick={() => onPageChange(0)} className="mt-3 min-h-11 text-[var(--dash-accent)]">
          첫 페이지
        </button>
      </div>
    ) : null
  return (
    <section className="relative rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] backdrop-blur-[12px]">
      <div className="flex items-center justify-between px-6 pt-[11px] pb-3">
        <span className="text-[12px] leading-[1.2] text-[var(--dash-muted)]">총 {page.totalElements.toLocaleString('ko-KR')}개</span>
        <div className="flex gap-[10px]">
          <div
            role="group"
            aria-label="아이템 종류"
            className="flex h-[38px] items-center gap-1 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] p-1"
          >
            {([[false, '전체'], [true, '물욕템']] as const).map(([value, label]) => (
              <button
                key={label}
                type="button"
                aria-pressed={luckyOnly === value}
                onClick={() => onLuckyOnlyChange(value)}
                className={`flex h-7 items-center rounded-[10px] px-4 text-[14px] leading-[1.2] ${luckyOnly === value ? 'bg-[var(--dash-accent)]/9 text-[var(--dash-accent)]' : 'text-[var(--dash-muted)]'}`}
              >
                {label}
              </button>
            ))}
          </div>
          <GlassSelect
            label="보스 필터"
            value={bossName}
            size="sm"
            menu="compact"
            align="right"
            menuClassName="w-[220px]"
            options={[{ value: null, label: '전체 보스' }, ...bosses.map((name) => ({ value: name, label: name }))]}
            onChange={onBossChange}
          />
          <GlassSelect
            label="표시 건수"
            value={page.size as BossItemPageSize}
            size="sm"
            menu="compact"
            align="right"
            menuClassName="w-[140px]"
            triggerLabel={`${page.size}개씩 보기`}
            options={pageSizes.map((value) => ({ value, label: `${value}개씩` }))}
            onChange={onPageSizeChange}
          />
        </div>
      </div>
      <div className="overflow-x-auto">
        <div role="table" aria-label="보스 아이템 획득 이력" className="min-w-[1344px]">
          <div
            role="row"
            className="flex h-11 items-center gap-6 bg-[var(--dash-track)] px-6 text-[12px] text-[var(--dash-muted)]"
          >
            {columns.map(([label, width]) => (
              <span key={label} role="columnheader" className={`shrink-0 ${width}`}>
                {label}
              </span>
            ))}
          </div>
          {status ??
            page.content.map((item, index) => (
              <div
                key={`${item.periodStart} ~ ${item.periodEnd}-${item.characterName}-${item.itemName}-${index}`}
                role="row"
                className="flex h-[62px] items-center gap-6 border-b border-[var(--dash-border)] px-6"
              >
                <span role="cell" className={`${cell} w-[240px] [font-family:Inter,sans-serif]`}>
                  {item.periodStart} ~ {item.periodEnd}
                </span>
                <span role="cell" className={`${cell} w-[150px]`}>
                  {item.characterName}
                </span>
                <span role="cell" className={`${cell} w-[150px]`}>
                  {item.bossName}
                </span>
                <span role="cell" className="flex w-[100px] shrink-0">
                  <StatsDifficultyBadge difficulty={item.difficulty} />
                </span>
                <span role="cell" className={`${cell} min-w-[560px] flex-1`}>
                  {item.itemName}
                </span>
              </div>
            ))}
        </div>
      </div>
      {page.totalElements > 0 && (
        <Pagination
          label="보스 아이템 획득 이력 페이지"
          page={page.page}
          totalPages={page.totalPages}
          onPageChange={onPageChange}
        />
      )}
    </section>
  )
}
