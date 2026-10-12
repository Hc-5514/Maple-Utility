import { useEffect, useId, useRef, useState } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from '../dashboard/assets'
import { characterAssets } from './assets'
import { controlButton } from './styles'

interface Option {
  value: string
  label: string
}

interface Props {
  label: string
  value: string
  options: Option[]
  onChange: (value: string) => void
  icon?: string
}

export default function PeriodMenu({ label, value, options, onChange, icon }: Props) {
  const { theme } = useThemeStore()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const selected = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  return (
    <div
      ref={root}
      className="relative"
      onKeyDown={(event) => {
        if (event.key === 'Escape') setOpen(false)
      }}
    >
      <button
        type="button"
        aria-label={`${label}: ${selected?.label ?? ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
        className={controlButton}
      >
        {icon && <img src={icon} alt="" className="size-4" />}
        {selected?.label}
        <img src={characterAssets[theme].imgChev} alt="" className={`size-4 ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div
          id={menuId}
          role="listbox"
          aria-label={label}
          className="absolute top-[48px] right-0 z-30 min-w-full overflow-hidden rounded-xl border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-[7px] shadow-[var(--dash-menu-shadow)] backdrop-blur-[16px]"
        >
          <div className="period-menu-scroll flex max-h-[196px] flex-col gap-1 overflow-y-auto">
            {options.map((option) => (
              <button
                key={option.value}
                type="button"
                role="option"
                aria-selected={option.value === value}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
                className={`flex min-h-[40px] items-center justify-between gap-3 rounded-lg px-[14px] text-[14px] whitespace-nowrap ${option.value === value ? 'bg-[var(--dash-selected)] font-semibold text-[var(--dash-accent)]' : 'hover:bg-[var(--dash-track)]'}`}
              >
                {option.label}
                {option.value === value && <img src={dashboardAssets[theme].imgCheck} alt="" className="shrink-0" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
