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
}
export default function ThemedConfirmDialog({
  title,
  children,
  onCancel,
  onConfirm,
  confirmLabel = '변경사항 버리고 이동',
  busy = false,
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
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus()
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
  }, [onCancel])
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
        <div className="text-sm leading-relaxed text-[var(--dash-muted)]">{children}</div>
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" disabled={busy} onClick={onCancel} className="settings-button">
            취소
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onConfirm}
            className="settings-button settings-primary"
          >
            {busy ? '처리 중...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
