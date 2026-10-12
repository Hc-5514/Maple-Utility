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
  const [active, setActive] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLDivElement>(null)
  const id = useId()
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  )
  const selected = options.find((option) => option.value === value)

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
    list.current
      ?.querySelector<HTMLElement>(`[data-index="${active}"]`)
      ?.scrollIntoView?.({ block: 'nearest' })
  }, [open, active])

  const openMenu = () => {
    setActive(selectedIndex)
    setOpen(true)
  }
  const close = () => {
    setOpen(false)
    trigger.current?.focus()
  }
  const choose = (index: number) => {
    onChange(options[index].value)
    close()
  }
  const onListKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown') setActive((index) => Math.min(options.length - 1, index + 1))
    else if (event.key === 'ArrowUp') setActive((index) => Math.max(0, index - 1))
    else if (event.key === 'Home') setActive(0)
    else if (event.key === 'End') setActive(options.length - 1)
    else if (event.key === 'Enter' || event.key === ' ') choose(active)
    else if (event.key === 'Escape') close()
    else if (event.key === 'Tab') setOpen(false)
    else return
    event.preventDefault()
  }

  const triggerTone = !open
    ? 'border-[var(--dash-border)] bg-[var(--dash-card)] text-[var(--dash-text)]'
    : openStyle === 'tint'
      ? 'border-[var(--dash-accent)]/31 bg-[var(--dash-selected)] text-[var(--dash-accent)]'
      : 'border-[var(--dash-accent)]/60 bg-[var(--dash-card)] text-[var(--dash-accent)]'
  const icon = open && openStyle === 'tint' ? assets.chevronAccent : assets.chevron

  return (
    <div ref={root} className="relative">
      <button
        ref={trigger}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-options`}
        aria-label={`${label}: ${triggerLabel ?? selected?.label ?? ''}`}
        onClick={() => (open ? setOpen(false) : openMenu())}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            openMenu()
          }
        }}
        className={`flex items-center gap-2 rounded-[10px] border px-[18px] text-[14px] font-bold whitespace-nowrap disabled:opacity-40 ${size === 'md' ? 'h-11' : 'h-[38px]'} ${triggerTone}`}
      >
        <img src={icon} alt="" className={open && openStyle === 'outline' ? 'rotate-180' : ''} />
        {triggerLabel ?? selected?.label}
      </button>
      {open && (
        <div
          ref={list}
          id={`${id}-options`}
          role="listbox"
          tabIndex={-1}
          aria-label={label}
          aria-activedescendant={`${id}-option-${active}`}
          onKeyDown={onListKeyDown}
          className={`absolute top-[calc(100%+6px)] z-30 flex max-h-[320px] flex-col overflow-y-auto rounded-xl border border-[var(--dash-border)] bg-[var(--dash-popover)] shadow-[var(--dash-popover-shadow)] backdrop-blur-[12px] outline-none ${align === 'right' ? 'right-0' : 'left-0'} ${menu === 'compact' ? 'p-2' : ''} ${menuClassName}`}
        >
          {options.map((option, index) => {
            const isSelected = option.value === value
            return (
              <div
                key={String(option.value)}
                id={`${id}-option-${index}`}
                data-index={index}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(index)}
                className={`flex shrink-0 cursor-pointer items-center text-[13px] whitespace-nowrap ${menu === 'compact' ? 'h-7 gap-[10px] rounded-md px-3 leading-5' : 'gap-2 px-[14px] py-[10px]'} ${isSelected ? 'font-bold text-[var(--dash-accent)]' : 'text-[var(--dash-text)]'} ${menu === 'list' && isSelected ? 'bg-[var(--dash-accent)]/9' : ''} ${active === index && !isSelected ? 'bg-[var(--dash-track)]' : ''}`}
              >
                <span className="flex size-4 shrink-0 items-center justify-center">
                  {isSelected && (
                    <img src={assets.check} alt="" className={menu === 'list' ? 'size-3' : 'size-4'} />
                  )}
                </span>
                {option.label}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
