import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from './assets'
import { worlds, worldNames } from './worlds'

interface Props {
  selectedWorlds: string[]
  onClose: () => void
  onSave: (worldNames: string[]) => Promise<unknown>
}
export default function WorldSelectionModal({ selectedWorlds, onClose, onSave }: Props) {
  const { theme } = useThemeStore()
  const assets = dashboardAssets[theme]
  const [selected, setSelected] = useState(selectedWorlds)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(false)
  const dialog = useRef<HTMLDivElement>(null)
  const titleId = useId()
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) onClose()
      if (event.key !== 'Tab') return
      const elements = dialog.current?.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled)'
      )
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
      previous?.focus()
      window.removeEventListener('keydown', onKey)
    }
  }, [onClose, saving])
  const save = async () => {
    setSaving(true)
    setError(false)
    try {
      await onSave(selected)
      onClose()
    } catch {
      setError(true)
    } finally {
      setSaving(false)
    }
  }
  return createPortal(
    <div
      data-theme={theme}
      className="dashboard-theme fixed inset-0 z-50 overflow-y-auto bg-[var(--dash-overlay)] backdrop-blur-[2px]"
      onClick={() => {
        if (!saving) onClose()
      }}
    >
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
        className="relative mx-auto mt-[min(22vh,248px)] mb-4 flex w-[calc(100%-32px)] max-w-[800px] flex-col gap-6 rounded-[24px] border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-[23px] text-[var(--dash-text)] shadow-[var(--dash-modal-shadow)] backdrop-blur-[16px] sm:p-[31px]"
      >
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <h2 id={titleId} className="text-[26px] font-bold leading-[1.25]">
              캐릭터 관리
            </h2>
            <button
              type="button"
              aria-label="닫기"
              disabled={saving}
              onClick={onClose}
              className="flex size-8 items-center justify-center rounded-[10px] bg-[var(--dash-track)] disabled:opacity-50"
            >
              <img src={assets.imgX} alt="" />
            </button>
          </div>
          <p className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">
            {theme === 'light'
              ? '관리할 월드를 여러 개 선택할 수 있습니다.'
              : '여러 월드를 동시에 선택할 수 있습니다.'}
          </p>
        </div>
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-[16px] font-bold">월드 선택</h3>
            <div className={theme === 'light' ? 'flex gap-3' : 'flex gap-2'}>
              <button
                type="button"
                disabled={saving}
                onClick={() => setSelected(worldNames)}
                className={`rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] font-bold ${theme === 'light' ? 'h-10 px-4 text-[14px]' : 'h-8 px-3 text-[13px] text-[var(--dash-muted)]'}`}
              >
                전체 월드 선택
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={() => setSelected([])}
                className={`flex items-center rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] font-bold text-[var(--dash-muted)] ${theme === 'light' ? 'h-10 gap-2 px-4 text-[14px]' : 'h-8 gap-[6px] px-3 text-[13px]'}`}
              >
                <img src={assets.imgReset} alt="" />
                초기화
              </button>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {worlds.map(([name, image]) => {
              const checked = selected.includes(name)
              return (
                <label
                  key={name}
                  className={`relative flex h-16 cursor-pointer items-center gap-3 rounded-xl border px-[18px] text-[15px] ${checked ? 'border-[var(--dash-accent)] bg-[var(--dash-selected)] font-bold text-[var(--dash-accent)]' : 'border-[var(--dash-border)] bg-[var(--dash-card)] font-medium'}`}
                >
                  <input
                    type="checkbox"
                    aria-label={name}
                    checked={checked}
                    disabled={saving}
                    onChange={() =>
                      setSelected((value) =>
                        checked ? value.filter((world) => world !== name) : [...value, name]
                      )
                    }
                    className="peer absolute size-[18px] opacity-0"
                  />
                  <img
                    src={checked ? assets.imgSelectionIndicator : assets.imgSelectionIndicator1}
                    alt=""
                    className="shrink-0 peer-focus-visible:outline-2 peer-focus-visible:outline-[var(--dash-accent)]"
                  />
                  <img src={image} alt="" className="size-7 shrink-0 object-contain" />
                  <span>{name}</span>
                </label>
              )
            })}
          </div>
        </div>
        {error && (
          <p role="alert" className="text-sm text-red-500">
            월드 선택을 저장하지 못했습니다. 다시 시도해 주세요.
          </p>
        )}
        <div
          className={`flex justify-end ${theme === 'dark' ? 'border-t border-[var(--dash-border)] pt-6' : ''}`}
        >
          <button
            type="button"
            disabled={saving}
            onClick={() => void save()}
            className="h-11 rounded-[10px] bg-[var(--dash-accent)] px-[22px] text-[14px] font-bold text-[var(--dash-button-text)] disabled:opacity-50"
          >
            {saving ? '저장 중...' : '선택 완료'}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
