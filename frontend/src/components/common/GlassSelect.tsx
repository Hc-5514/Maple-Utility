import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from '../hunting/assets'

export interface GlassSelectOption<T> {
  value: T
  label: string
}

interface GlassSelectProps<T> {
  value: T
  options: GlassSelectOption<T>[]
  onChange: (value: T) => void
  label: string
  triggerLabel?: string
  placeholder?: string
  variant?: 'button' | 'field'
  size?: 'md' | 'sm'
  openStyle?: 'tint' | 'outline'
  menu?: 'list' | 'compact'
  align?: 'left' | 'right'
  menuClassName?: string
  disabled?: boolean
}

export default function GlassSelect<T extends string | number | null>({
  value,
  options,
  onChange,
  label,
  triggerLabel,
  placeholder,
  variant = 'button',
  size = 'md',
  openStyle = 'outline',
  menu = 'list',
  align = 'left',
  menuClassName = '',
  disabled = false,
}: GlassSelectProps<T>) {
  const { theme } = useThemeStore()
  const assets = huntingAssets[theme]
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLDivElement>(null)
  const id = useId()
  const field = variant === 'field'
  const selectedIndex = options.findIndex((option) => option.value === value)
  const selected = options[selectedIndex]
  const shownLabel = triggerLabel ?? (value === null && placeholder ? placeholder : selected?.label)

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  useEffect(() => {
    if (!open) return
    list.current?.focus()
    const index = active >= 0 ? active : selectedIndex
    list.current
      ?.querySelector<HTMLElement>(`[data-index="${index}"]`)
      ?.scrollIntoView?.({ block: 'nearest' })
  }, [open, active, selectedIndex])

  const openMenu = (fromKeyboard: boolean) => {
    setActive(fromKeyboard ? Math.max(0, selectedIndex) : -1)
    setOpen(true)
  }
  const close = () => {
    setOpen(false)
    trigger.current?.focus()
  }
  const choose = (index: number) => {
    if (index < 0) return
    onChange(options[index].value)
    close()
  }
  const onListKeyDown = (event: KeyboardEvent) => {
    const from = active >= 0 ? active : Math.max(0, selectedIndex)
    if (event.key === 'ArrowDown') setActive(Math.min(options.length - 1, active < 0 ? from : from + 1))
    else if (event.key === 'ArrowUp') setActive(Math.max(0, active < 0 ? from : from - 1))
    else if (event.key === 'Home') setActive(0)
    else if (event.key === 'End') setActive(options.length - 1)
    else if (event.key === 'Enter' || event.key === ' ') choose(from)
    else if (event.key === 'Escape') close()
    else if (event.key === 'Tab') setOpen(false)
    else return
    event.preventDefault()
  }

  const triggerTone = field
    ? open
      ? 'border-[var(--dash-accent)] bg-[var(--dash-accent)]/9'
      : 'border-[var(--dash-border)] bg-[var(--dash-card)]'
    : !open
      ? 'border-[var(--dash-border)] bg-[var(--dash-card)] text-[var(--dash-text)]'
      : openStyle === 'tint'
        ? 'border-[var(--dash-accent)]/31 bg-[var(--dash-selected)] text-[var(--dash-accent)]'
        : 'border-[var(--dash-accent)]/60 bg-[var(--dash-card)] text-[var(--dash-accent)]'
  const icon = field
    ? open
      ? assets.chevronUp
      : assets.chevron
    : open && openStyle === 'tint'
      ? assets.chevronAccent
      : assets.chevron
  const compact = field || menu === 'compact'

  return (
    <div ref={root} className={field ? 'relative w-full' : 'relative'}>
      <button
        ref={trigger}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-options`}
        aria-label={`${label}: ${shownLabel ?? ''}`}
        onClick={() => (open ? setOpen(false) : openMenu(false))}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            openMenu(true)
          }
        }}
        className={
          field
            ? `flex h-12 w-full items-center gap-3 rounded-[10px] border px-4 text-left text-[14px] leading-5 disabled:opacity-50 ${triggerTone}`
            : `flex items-center gap-2 rounded-[10px] border px-[18px] text-[14px] font-bold whitespace-nowrap disabled:opacity-40 ${size === 'md' ? 'h-11' : 'h-[38px]'} ${triggerTone}`
        }
      >
        {!field && (
          <img src={icon} alt="" className={open && openStyle === 'outline' ? 'rotate-180' : ''} />
        )}
        {field ? (
          <span
            className={`min-w-0 flex-1 truncate ${value === null ? 'text-[var(--dash-muted)]' : 'text-[var(--dash-text)]'}`}
          >
            {shownLabel}
          </span>
        ) : (
          shownLabel
        )}
        {field && <img src={icon} alt="" />}
      </button>
      {open && (
        <div
          className={`absolute z-30 overflow-hidden rounded-xl border border-[var(--dash-border)] bg-[var(--dash-popover)] shadow-[var(--dash-popover-shadow)] backdrop-blur-[12px] ${field ? 'top-[calc(100%+8px)] right-0 left-0 p-2' : `top-[calc(100%+6px)] ${align === 'right' ? 'right-0' : 'left-0'} ${compact ? 'p-2' : ''}`} ${menuClassName}`}
        >
          <div
            ref={list}
            id={`${id}-options`}
            role="listbox"
            tabIndex={-1}
            aria-label={label}
            aria-activedescendant={active >= 0 ? `${id}-option-${active}` : undefined}
            onKeyDown={onListKeyDown}
            className={`glass-select-scroll flex flex-col overflow-y-auto outline-none ${compact ? 'max-h-[238px]' : 'max-h-[320px]'}`}
          >
            {options.map((option, index) => {
              const isSelected = option.value === value
              const isActive = active === index
              return (
                <div
                  key={String(option.value)}
                  id={`${id}-option-${index}`}
                  data-index={index}
                  role="option"
                  aria-selected={isSelected}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => choose(index)}
                  className={`flex shrink-0 cursor-pointer items-center text-[13px] whitespace-nowrap ${compact ? 'h-7 gap-[10px] rounded-md px-3 leading-5' : 'gap-2 px-[14px] py-[10px]'} ${isSelected || (compact && isActive) ? 'font-bold text-[var(--dash-accent)]' : 'text-[var(--dash-text)]'} ${(!compact && isSelected) || (compact && isActive) ? 'bg-[var(--dash-accent)]/9' : ''} ${!compact && isActive && !isSelected ? 'bg-[var(--dash-track)]' : ''}`}
                >
                  <span className="flex size-4 shrink-0 items-center justify-center">
                    {isSelected && (
                      <img src={assets.check} alt="" className={compact ? 'size-4' : 'size-3'} />
                    )}
                  </span>
                  <span className="truncate">{option.label}</span>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
