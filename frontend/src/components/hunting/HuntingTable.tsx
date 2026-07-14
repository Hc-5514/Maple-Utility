import { useState } from 'react'
import Modal from '../common/Modal'
import type { HuntingRecord } from '../../types'

interface Props {
  records: HuntingRecord[]
  characterMap: Map<number, string>
  onEdit: (id: number) => void
  onDelete: (id: number) => void
}

export default function HuntingTable({ records, characterMap, onEdit, onDelete }: Props) {
  const [confirmId, setConfirmId] = useState<number | null>(null)

  const totalMeso = records.reduce((sum, r) => sum + r.mesoEarned, 0)
  const totalSolErda = records.reduce((sum, r) => sum + r.solErdaEarned, 0)

  if (records.length === 0) {
    return (
      <div className="rounded-xl bg-[#2d2d44] p-10 text-center text-sm text-white/40">
        기록이 없습니다
      </div>
    )
  }

  return (
    <>
      <div className="overflow-x-auto rounded-xl bg-[#2d2d44]">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-white/10 text-left text-white/50">
              <th className="px-4 py-3 font-medium">날짜</th>
              <th className="px-4 py-3 font-medium">캐릭터</th>
              <th className="px-4 py-3 font-medium text-right">메소</th>
              <th className="px-4 py-3 font-medium text-right">솔 에르다</th>
              <th className="px-4 py-3 font-medium">사냥터</th>
              <th className="px-4 py-3 font-medium text-right">시간(분)</th>
              <th className="px-4 py-3 font-medium">메모</th>
              <th className="px-4 py-3 font-medium"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {records.map((record) => (
              <tr key={record.id} className="text-white/80 hover:bg-white/5">
                <td className="px-4 py-3 tabular-nums">{record.recordDate}</td>
                <td className="px-4 py-3">
                  {characterMap.get(record.characterId) ?? `#${record.characterId}`}
                </td>
                <td className="px-4 py-3 text-right tabular-nums text-[#4ade80]">
                  {record.mesoEarned.toLocaleString()}
                </td>
                <td className="px-4 py-3 text-right tabular-nums">
                  {record.solErdaEarned}
                </td>
                <td className="px-4 py-3">{record.huntingGround ?? '—'}</td>
                <td className="px-4 py-3 text-right tabular-nums">
                  {record.playDurationMin !== null ? record.playDurationMin : '—'}
                </td>
                <td className="max-w-[160px] truncate px-4 py-3 text-white/50">
                  {record.memo ?? '—'}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onEdit(record.id)}
                      className="rounded px-2 py-1 text-xs text-white/60 hover:bg-white/10 hover:text-white"
                    >
                      수정
                    </button>
                    <button
                      onClick={() => setConfirmId(record.id)}
                      className="rounded px-2 py-1 text-xs text-[#f87171]/70 hover:bg-[#f87171]/10 hover:text-[#f87171]"
                    >
                      삭제
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-white/10 font-semibold text-white">
              <td className="px-4 py-3 text-white/50" colSpan={2}>합계</td>
              <td className="px-4 py-3 text-right tabular-nums text-[#4ade80]">
                {totalMeso.toLocaleString()}
              </td>
              <td className="px-4 py-3 text-right tabular-nums">{totalSolErda}</td>
              <td colSpan={4} />
            </tr>
          </tfoot>
        </table>
      </div>

      <Modal
        isOpen={confirmId !== null}
        onClose={() => setConfirmId(null)}
        title="사냥 기록 삭제"
      >
        <p className="mb-6 text-sm text-white/70">이 기록을 삭제하시겠습니까? 되돌릴 수 없습니다.</p>
        <div className="flex justify-end gap-3">
          <button
            onClick={() => setConfirmId(null)}
            className="rounded-lg px-4 py-2 text-sm text-white/60 hover:bg-white/10"
          >
            취소
          </button>
          <button
            onClick={() => {
              if (confirmId !== null) {
                onDelete(confirmId)
                setConfirmId(null)
              }
            }}
            className="rounded-lg bg-[#f87171] px-4 py-2 text-sm font-semibold text-white hover:bg-[#f87171]/80"
          >
            삭제
          </button>
        </div>
      </Modal>
    </>
  )
}
