import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from '../dashboard/assets'

interface Props {
  title: string
  description?: ReactNode
  width: number
  top: number
  gap?: number
  busy?: boolean
  onClose: () => void
  children: ReactNode
  actions: ReactNode
}

const FOCUSABLE = 'button:not(:disabled), input:not(:disabled), [tabindex="0"]'

export default function ThemedDialog({ title, description, width, top, gap = 24, busy = false, onClose, children, actions }: Props) {
  const { theme } = useThemeStore()
  const titleId = useId()
  const dialog = useRef<HTMLDivElement>(null)
  const busyRef = useRef(busy)
  const closeRef = useRef(onClose)
  useEffect(() => {
    busyRef.current = busy
    closeRef.current = onClose
  })
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) closeRef.current()
      if (event.key !== 'Tab') return
      const elements = dialog.current?.querySelectorAll<HTMLElement>(FOCUSABLE)
      if (!elements?.length) return
      const first = elements[0],
        last = elements[elements.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = overflow
      window.removeEventListener('keydown', onKey)
      previous?.focus()
    }
  }, [])

  return createPortal(
    <div
      data-theme={theme}
      className="dashboard-theme fixed inset-0 z-50 overflow-y-auto bg-[var(--dash-overlay)] leading-[1.2] backdrop-blur-[2px]"
      onClick={() => {
        if (!busy) onClose()
      }}
    >
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        style={{ maxWidth: width, marginTop: `min(${((top / 900) * 100).toFixed(2)}vh, ${top}px)`, gap }}
        className="relative mx-auto mb-4 flex w-[calc(100%-32px)] flex-col rounded-[24px] outline-none border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-8 text-[var(--dash-text)] shadow-[var(--dash-modal-shadow)] backdrop-blur-[16px]"
      >
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-3">
            <h2 id={titleId} className="text-[26px] font-bold">
              {title}
            </h2>
            <button
              type="button"
              aria-label="닫기"
              disabled={busy}
              onClick={onClose}
              className="ml-auto flex size-8 shrink-0 items-center justify-center rounded-[10px] bg-[var(--dash-track)] disabled:opacity-50"
            >
              <img src={dashboardAssets[theme].imgX} alt="" className="size-[18px]" />
            </button>
          </div>
          {description && <p className="text-[14px] text-[var(--dash-muted)]">{description}</p>}
        </div>
        {children}
        <div className="h-px bg-[var(--dash-border)]" />
        <div className="flex justify-end gap-2">{actions}</div>
      </div>
    </div>,
    document.body
  )
}

export const dialogCancel = 'settings-button px-[22px]'
export const dialogConfirm = 'settings-button settings-primary'
