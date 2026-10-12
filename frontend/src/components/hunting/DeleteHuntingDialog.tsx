import { useEffect } from 'react'
import type { HuntingRecord } from '../../types'

export default function DeleteHuntingDialog({
  record,
  characterName,
  pending,
  onCancel,
  onConfirm,
}: {
  record: HuntingRecord
  characterName: string
  pending: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  useEffect(() => {
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending) onCancel()
    }
    document.addEventListener('keydown', close)
    return () => document.removeEventListener('keydown', close)
  }, [pending, onCancel])
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--dash-overlay)] p-4"
      onMouseDown={(event) => event.target === event.currentTarget && !pending && onCancel()}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-hunting-title"
        className="w-full max-w-[420px] rounded-3xl border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-[31px] shadow-[var(--dash-modal-shadow)] backdrop-blur-[16px]"
      >
        <h2 id="delete-hunting-title" className="text-[22px] font-bold">
          사냥 기록 삭제
        </h2>
        <p className="mt-3 text-[14px] leading-[1.45] text-[var(--dash-muted)]">
          {record.recordDate} · {characterName} 기록을 삭제할까요? 삭제한 기록은 되돌릴 수 없습니다.
        </p>
        <div className="mt-7 flex justify-end gap-2">
          <button
            type="button"
            autoFocus
            disabled={pending}
            onClick={onCancel}
            className="h-11 rounded-[10px] px-[18px] text-[14px] font-bold text-[var(--dash-muted)]"
          >
            취소
          </button>
          <button
            type="button"
            disabled={pending}
            onClick={onConfirm}
            className="h-11 rounded-[10px] bg-[#e11d48] px-[18px] text-[14px] font-bold text-white disabled:opacity-60"
          >
            {pending ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </div>
    </div>
  )
}
