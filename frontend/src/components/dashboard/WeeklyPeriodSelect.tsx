import { useEffect, useRef, useState } from 'react'
import { recentWeeks } from '../../utils/date'
import { dashboardAssets } from './assets'
import { useThemeStore } from '../../stores/themeStore'
export { recentWeeks }
export default function WeeklyPeriodSelect({
  value,
  onChange,
}: {
  value: string
  onChange: (date: string) => void
}) {
  const { theme } = useThemeStore()
  const assets = dashboardAssets[theme]
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])
  const weeks = recentWeeks()
  const selectedWeek = weeks.find((week) => week.date === value) ?? weeks[0]
  const selectedLabel = `${selectedWeek.start.replace(/-/g, '.')} ~ ${selectedWeek.end.replace(/-/g, '.')}`
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
        aria-expanded={open}
        aria-controls="weekly-period-options"
        onClick={() => setOpen(!open)}
        className={`flex h-11 w-[245px] items-center rounded-[10px] border border-[var(--dash-accent)]/50 bg-[var(--dash-accent)]/9 text-[14px] font-bold ${theme === 'light' ? 'gap-3 px-5' : 'gap-2 px-6'}`}
      >
        <img src={assets.imgControlIcon3} alt="" className={open ? 'rotate-180' : ''} />
        <span className="whitespace-nowrap [font-family:'Noto_Sans_KR',sans-serif]">
          {selectedLabel}
        </span>
      </button>
      {open && (
        <div
          id="weekly-period-options"
          className="absolute top-[52px] right-0 z-30 w-[245px] overflow-hidden rounded-xl border border-[var(--dash-border)] bg-[var(--dash-dialog)] p-[7px] shadow-[var(--dash-menu-shadow)] backdrop-blur-[16px]"
        >
          <div
            className={`period-menu-scroll flex max-h-[196px] flex-col gap-1 overflow-y-auto ${theme === 'light' ? 'w-[calc(100%+8px)]' : 'w-[calc(100%+4px)]'}`}
          >
            {weeks.map((week) => (
              <button
                key={week.start}
                type="button"
                aria-pressed={value === week.date}
                onClick={() => {
                  onChange(week.date)
                  setOpen(false)
                }}
                className={`flex min-h-[46px] items-center justify-between rounded-lg pl-[14px] text-[14px] leading-[17px] [font-family:Inter,sans-serif] ${theme === 'light' ? 'pr-[14px]' : 'pr-1'} ${value === week.date ? 'bg-[var(--dash-selected)] font-semibold text-[var(--dash-accent)]' : 'hover:bg-[var(--dash-track)]'}`}
              >
                <span className="whitespace-nowrap">
                  {week.start.replace(/-/g, '.')} ~ {week.end.replace(/-/g, '.')}
                </span>
                {value === week.date && <img src={assets.imgCheck} alt="" className="shrink-0" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
