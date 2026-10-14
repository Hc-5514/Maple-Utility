import type { ReactNode } from 'react'
import GlassSelect from '../common/GlassSelect'
import Pagination from '../common/Pagination'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from './assets'
import { formatDuration } from './format'
import type { HuntingPageSize } from '../../hooks/useHunting'
import type { HuntingRecord, HuntingSummary } from '../../types'

const pageSizes: HuntingPageSize[] = [10, 20, 30]
const columns: [string, string][] = [
  ['날짜', 'w-[105px]'],
  ['캐릭터', 'w-[120px]'],
  ['메소', 'w-[110px]'],
  ['솔 에르다 조각', 'w-[96px]'],
  ['사냥터', 'w-[180px]'],
  ['사냥 시간', 'w-[85px]'],
  ['메모', 'min-w-0 flex-1'],
  ['관리', 'w-[110px]'],
]
const inter = '[font-family:Inter,sans-serif]'

interface Props {
  records: HuntingRecord[]
  summary?: HuntingSummary
  characterNames: Map<number, string>
  page: number
  totalPages: number
  size: HuntingPageSize
  status: ReactNode
  onSizeChange: (size: HuntingPageSize) => void
  onPageChange: (page: number) => void
  onEdit: (id: number) => void
  onDelete: (record: HuntingRecord) => void
}

export default function HuntingTable({
  records,
  summary,
  characterNames,
  page,
  totalPages,
  size,
  status,
  onSizeChange,
  onPageChange,
  onEdit,
  onDelete,
}: Props) {
  const { theme } = useThemeStore()
  const assets = huntingAssets[theme]
  const cell = 'shrink-0 text-[13px]'
  return (
    <section className="relative z-10 rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px]">
      <div className="flex items-center justify-between px-6 pt-[23px] pb-6">
        <div className="flex items-center gap-3">
          <h2 className="text-[18px] leading-[1.45]">사냥 내역</h2>
          <span className="rounded-full border border-[var(--dash-accent)]/16 bg-[var(--dash-accent)]/8 px-[10px] py-1 text-[11px] font-bold text-[var(--dash-accent)]">
            {summary?.recordCount ?? 0}개 기록
          </span>
        </div>
        <GlassSelect
          label="표시 건수"
          value={size}
          size="sm"
          menu="compact"
          align="right"
          menuClassName="w-[140px]"
          triggerLabel={`${size}개씩 보기`}
          options={pageSizes.map((value) => ({ value, label: `${value}개씩` }))}
          onChange={onSizeChange}
        />
      </div>
      <div className="overflow-x-auto">
        <div role="table" aria-label="사냥 내역" className="min-w-[1280px]">
          <div
            role="row"
            className="flex h-12 items-center gap-[25px] bg-[var(--dash-track)] px-6 text-center text-[12px] text-[var(--dash-muted)]"
          >
            {columns.map(([label, width]) => (
              <span
                key={label}
                role="columnheader"
                className={width.includes('flex-1') ? width : `shrink-0 ${width}`}
              >
                {label}
              </span>
            ))}
          </div>
          {status ??
            records.map((record) => (
              <div
                key={record.id}
                role="row"
                className="flex h-[88px] items-center gap-[25px] border-b border-[var(--dash-border)] px-6"
              >
                <span role="cell" className={`${cell} w-[105px] text-center`}>
                  {record.recordDate}
                </span>
                <span role="cell" className={`${cell} w-[120px] truncate text-center`}>
                  {characterNames.get(record.characterId) ?? `#${record.characterId}`}
                </span>
                <span role="cell" className={`${cell} ${inter} w-[110px] text-right text-[var(--dash-meso)]`}>
                  {record.mesoEarned.toLocaleString('ko-KR')}
                </span>
                <span role="cell" className={`${cell} ${inter} w-[96px] text-center`}>
                  {record.solErdaEarned}
                </span>
                <span role="cell" className={`${cell} w-[180px] truncate text-center`}>
                  {record.huntingGround ?? '-'}
                </span>
                <span role="cell" className={`${cell} ${inter} w-[85px] text-center`}>
                  {formatDuration(record.playDurationMin)}
                </span>
                <span role="cell" className="min-w-0 flex-1 truncate text-[13px]">
                  {record.memo || '-'}
                </span>
                <span role="cell" className="flex w-[110px] shrink-0 justify-center gap-[18px]">
                  <button type="button" aria-label={`${record.recordDate} 기록 수정`} onClick={() => onEdit(record.id)}>
                    <img src={assets.pencil} alt="" />
                  </button>
                  <button type="button" aria-label={`${record.recordDate} 기록 삭제`} onClick={() => onDelete(record)}>
                    <img src={assets.trash} alt="" />
                  </button>
                </span>
              </div>
            ))}
        </div>
      </div>
      <Pagination label="사냥 기록 페이지" page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </section>
  )
}
