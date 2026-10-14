import { useRef } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from '../hunting/assets'
import { formatDotDate } from '../hunting/format'

export default function DateButton({
  label,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  value: string
  min?: string
  max?: string
  onChange: (value: string) => void
}) {
  const { theme } = useThemeStore()
  const input = useRef<HTMLInputElement>(null)
  return (
    <div className="relative">
      <button
        type="button"
        aria-label={`${label} ${value}`}
        onClick={() => {
          const element = input.current
          if (!element) return
          // 팝업의 포커스 소유자를 먼저 확립해야 바깥 클릭·선택 후 blur로 닫힘
          element.focus({ preventScroll: true })
          if (typeof element.showPicker === 'function') element.showPicker()
        }}
        className="flex h-11 items-center gap-2 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-[18px] text-[14px] font-bold whitespace-nowrap"
      >
        <img src={huntingAssets[theme].calendar} alt="" />
        {formatDotDate(value)}
      </button>
      <input
        ref={input}
        type="date"
        tabIndex={-1}
        aria-label={label}
        value={value}
        min={min}
        max={max}
        onChange={(event) => {
          if (event.target.value) onChange(event.target.value)
          event.target.blur()
        }}
        className="pointer-events-none absolute inset-0 opacity-0"
      />
    </div>
  )
}
