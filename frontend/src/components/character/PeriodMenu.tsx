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
  compact?: boolean
  // 펼친 목록을 버튼과 같은 너비로 고정
  fitWidth?: boolean
  // 선택값이 옵션에 없을 때 버튼에 보여줄 안내 문구 (없으면 첫 옵션 표시)
  placeholder?: string
}

// 펼친 목록의 대략적인 최대 높이(옵션 40px + 간격 4px, 패딩 7px, 최대 196px 스크롤)
const MENU_PADDING = 14
const OPTION_HEIGHT = 44
const MENU_MAX_HEIGHT = 196 + MENU_PADDING

export default function PeriodMenu({ label, value, options, onChange, icon, compact = false, fitWidth = false, placeholder }: Props) {
  const { theme } = useThemeStore()
  const [open, setOpen] = useState(false)
  const [dropUp, setDropUp] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const selected = options.find((option) => option.value === value) ?? (placeholder ? undefined : options[0])

  // 스크롤 영역(드랍 모달 목록)이나 화면 아래 공간이 부족하고 위 공간이 더 넓으면 위로 연다
  const toggle = () => {
    if (!open && root.current) {
      const rect = root.current.getBoundingClientRect()
      const scrollArea = root.current.closest('.dialog-scroll')
      const bounds = scrollArea
        ? scrollArea.getBoundingClientRect()
        : { top: 0, bottom: window.innerHeight }
      const menuHeight = Math.min(options.length * OPTION_HEIGHT + MENU_PADDING, MENU_MAX_HEIGHT)
      const below = bounds.bottom - rect.bottom
      const above = rect.top - bounds.top
      setDropUp(below < menuHeight && above > below)
    }
    setOpen(!open)
  }

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
        aria-label={`${label}: ${selected?.label ?? placeholder ?? ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={toggle}
        className={compact ? `${controlButton} h-9 pr-3 pl-[14px]` : fitWidth ? `${controlButton} h-11` : controlButton}
      >
        {icon && <img src={icon} alt="" className="size-4" />}
        {selected?.label ?? placeholder}
        <img src={characterAssets[theme].imgChev} alt="" className={`size-4 ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div
          id={menuId}
          role="listbox"
          aria-label={label}
          className={`absolute right-0 z-30 ${fitWidth ? 'w-full' : 'min-w-full'} overflow-hidden rounded-xl border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-[7px] shadow-[var(--dash-menu-shadow)] backdrop-blur-[16px] ${compact ? (dropUp ? 'bottom-[44px]' : 'top-[44px]') : fitWidth ? (dropUp ? 'bottom-[52px]' : 'top-[52px]') : (dropUp ? 'bottom-[48px]' : 'top-[48px]')}`}
        >
          <div className="period-menu-scroll flex max-h-[196px] flex-col gap-1 overflow-x-hidden overflow-y-auto">
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
                className={`flex min-h-[40px] items-center justify-between rounded-lg text-[14px] whitespace-nowrap ${fitWidth ? 'gap-2 px-3' : 'gap-3 px-[14px]'} ${option.value === value ? 'bg-[var(--dash-selected)] font-semibold text-[var(--dash-accent)]' : 'hover:bg-[var(--dash-track)]'}`}
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
