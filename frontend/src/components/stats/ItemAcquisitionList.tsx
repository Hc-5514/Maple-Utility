import { ChevronLeft, ChevronRight } from 'lucide-react'
import DifficultyBadge from '../common/DifficultyBadge'
import type { PageResponse, StatsBossItem } from '../../types'
import type { BossItemPageSize } from '../../hooks/useStats'

interface Props {
  page: PageResponse<StatsBossItem>
  onPageChange: (page: number) => void
  onPageSizeChange: (size: BossItemPageSize) => void
}

export default function ItemAcquisitionList({ page, onPageChange, onPageSizeChange }: Props) {
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-white/60">
        <span>총 {page.totalElements.toLocaleString()}건</span>
        <label className="flex items-center gap-2">
          <span>표시 건수</span>
          <select
            aria-label="페이지당 표시 건수"
            value={page.size}
            onChange={(event) => onPageSizeChange(Number(event.target.value) as BossItemPageSize)}
            className="rounded border border-white/20 bg-[#2d2d44] px-2 py-1.5 text-white"
          >
            <option value={10}>10개</option>
            <option value={20}>20개</option>
            <option value={30}>30개</option>
          </select>
        </label>
      </div>
      {page.content.length === 0 ? (
        <div className="rounded-lg bg-[#2d2d44] p-8 text-center text-sm text-white/40">
          {page.totalElements === 0 ? '획득 기록이 없습니다' : (
            <>
              <p>현재 페이지에 표시할 기록이 없습니다</p>
              <button type="button" onClick={() => onPageChange(0)} className="mt-3 text-white underline">
                첫 페이지
              </button>
            </>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg bg-[#2d2d44]">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-white/50">
                <th className="px-4 py-3 font-medium">날짜</th>
                <th className="px-4 py-3 font-medium">캐릭터</th>
                <th className="px-4 py-3 font-medium">보스</th>
                <th className="px-4 py-3 font-medium">난이도</th>
                <th className="px-4 py-3 font-medium">아이템</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {page.content.map((item, idx) => (
                <tr key={`${item.acquiredDate}-${item.characterName}-${item.bossName}-${item.itemName}-${idx}`} className="text-white/80 hover:bg-white/5">
                  <td className="px-4 py-3 tabular-nums">{item.acquiredDate}</td>
                  <td className="px-4 py-3">{item.characterName}</td>
                  <td className="px-4 py-3">{item.bossName}</td>
                  <td className="px-4 py-3"><DifficultyBadge difficulty={item.difficulty} /></td>
                  <td className="px-4 py-3">{item.itemName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {page.totalPages > 1 && page.page < page.totalPages && (
        <nav aria-label="보스 아이템 획득 이력 페이지" className="flex items-center justify-center gap-3 text-sm text-white/80">
          <button
            type="button"
            aria-label="이전 페이지"
            title="이전 페이지"
            disabled={page.page === 0}
            onClick={() => onPageChange(page.page - 1)}
            className="grid size-8 place-items-center rounded border border-white/20 disabled:cursor-not-allowed disabled:opacity-40"
          ><ChevronLeft size={16} /></button>
          <span aria-live="polite" className="min-w-16 text-center tabular-nums">{page.page + 1} / {page.totalPages}</span>
          <button
            type="button"
            aria-label="다음 페이지"
            title="다음 페이지"
            disabled={page.page + 1 >= page.totalPages}
            onClick={() => onPageChange(page.page + 1)}
            className="grid size-8 place-items-center rounded border border-white/20 disabled:cursor-not-allowed disabled:opacity-40"
          ><ChevronRight size={16} /></button>
        </nav>
      )}
    </div>
  )
}
