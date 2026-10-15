import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from '../dashboard/assets'

interface Props {
  title: string
  children: ReactNode
  onCancel: () => void
  onConfirm: () => void
  confirmLabel?: string
  busy?: boolean
  // 파괴적 동작(영구 삭제 등)은 붉은색 확인 버튼
  confirmTone?: 'primary' | 'danger'
  confirmDisabled?: boolean
  busyLabel?: string
  // 첫 포커스 대상. 입력 폼이 있는 팝업은 input
  initialFocus?: 'button' | 'input'
  bodyClassName?: string
}
export default function ThemedConfirmDialog({
  title,
  children,
  onCancel,
  onConfirm,
  confirmLabel = '변경사항 버리고 이동',
  busy = false,
  confirmTone = 'primary',
  confirmDisabled = false,
  busyLabel = '처리 중...',
  initialFocus = 'button',
  bodyClassName = 'text-sm leading-relaxed text-[var(--dash-muted)]',
}: Props) {
  const { theme } = useThemeStore()
  const titleId = useId()
  const dialog = useRef<HTMLDivElement>(null)
  const busyRef = useRef(busy)
  useEffect(() => {
    busyRef.current = busy
  }, [busy])
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.querySelector<HTMLElement>(initialFocus)?.focus()
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) onCancel()
      if (event.key !== 'Tab') return
      const buttons = dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')
      if (!buttons?.length) return
      const first = buttons[0],
        last = buttons[buttons.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', key)
    return () => {
      document.body.style.overflow = overflow
      window.removeEventListener('keydown', key)
      previous?.focus()
    }
  }, [onCancel, initialFocus])
  return createPortal(
    <div
      data-theme={theme}
      className="dashboard-theme fixed inset-0 z-[60] flex items-center justify-center bg-[var(--dash-overlay)] p-4 backdrop-blur-[2px]"
      onClick={() => {
        if (!busy) onCancel()
      }}
    >
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-[480px] rounded-3xl border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-8 text-[var(--dash-text)] shadow-[var(--dash-modal-shadow)]"
      >
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 id={titleId} className="text-xl font-bold">
            {title}
          </h2>
          <button
            type="button"
            aria-label="닫기"
            disabled={busy}
            onClick={onCancel}
            className="flex size-8 items-center justify-center rounded-lg bg-[var(--dash-track)]"
          >
            <img src={dashboardAssets[theme].imgX} alt="" />
          </button>
        </div>
        <div className={bodyClassName}>{children}</div>
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" disabled={busy} onClick={onCancel} className="settings-button">
            취소
          </button>
          <button
            type="button"
            disabled={busy || confirmDisabled}
            onClick={onConfirm}
            className={`settings-button ${confirmTone === 'danger' ? 'settings-danger-solid' : 'settings-primary'}`}
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
