import { useEffect, useId, useRef, useState } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from '../hunting/assets'
import { formatDotDate } from '../hunting/format'
import { localDate } from '../../utils/date'

interface Props {
  label: string
  value: string
  weekday: 3 | 4
  min?: string
  max: string
  rangeStart?: string
  rangeEnd?: string
  onChange: (value: string) => void
}

function monthOf(value: string) {
  return `${value.slice(0, 7)}-01`
}
function dateAt(value: string) {
  return new Date(`${value}T12:00:00`)
}
function shiftMonth(value: string, offset: number) {
  const date = dateAt(value)
  date.setMonth(date.getMonth() + offset)
  return localDate(date)
}

/** 조회 필터 전용 목요일/수요일 달력. 실제 기록일의 자유 날짜 입력과 분리. */
export default function WeekDateButton(props: Props) {
  const { label, value, weekday, min, max, rangeStart, rangeEnd, onChange } = props
  const { theme } = useThemeStore()
  const [open, setOpen] = useState(false)
  const [month, setMonth] = useState(monthOf(value))
  const [popoverLeft, setPopoverLeft] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const pendingFocus = useRef<string | null>(null)
  const dialogId = useId()
  const headingId = useId()
  const allowed = (date: string) =>
    dateAt(date).getDay() === weekday && (!min || date >= min) && date <= max
  const first = dateAt(month)
  const dayCount = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate()
  const dates = Array.from(
    { length: dayCount },
    (_, index) => `${month.slice(0, 8)}${String(index + 1).padStart(2, '0')}`
  )
  const close = () => {
    setOpen(false)
    trigger.current?.focus()
  }

  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent | FocusEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('focusin', outside)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('focusin', outside)
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const date = pendingFocus.current ?? value
    pendingFocus.current = null
    const target =
      root.current?.querySelector<HTMLButtonElement>(
        `button[data-date="${date}"]:not(:disabled)`
      ) ??
      root.current?.querySelector<HTMLButtonElement>('button[data-date]:not(:disabled)') ??
      root.current?.querySelector<HTMLButtonElement>('[data-month-nav]')
    target?.focus()
  }, [open, month, value])

  return (
    <div ref={root} className="relative">
      <button
        ref={trigger}
        type="button"
        aria-label={`${label} ${value}`}
        aria-expanded={open}
        aria-controls={dialogId}
        aria-haspopup="dialog"
        onClick={() => {
          if (open) close()
          else {
            setMonth(monthOf(value))
            const x = root.current?.getBoundingClientRect().left ?? 0
            const width = Math.min(344, window.innerWidth - 32)
            setPopoverLeft(Math.max(16, Math.min(x, window.innerWidth - width - 16)) - x)
            setOpen(true)
          }
        }}
        className="flex h-11 items-center gap-2 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-[18px] text-[14px] font-bold whitespace-nowrap"
      >
        <img src={huntingAssets[theme].calendar} alt="" />
        {formatDotDate(value)}
      </button>
      {open && (
        <div
          id={dialogId}
          role="dialog"
          aria-labelledby={headingId}
          style={{ left: popoverLeft }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              event.stopPropagation()
              close()
            }
          }}
          className="absolute top-[calc(100%+8px)] left-0 z-50 flex w-[344px] max-w-[calc(100vw-32px)] flex-col gap-3 rounded-xl border border-[var(--dash-border)] bg-[var(--dash-popover)] p-4 text-[var(--dash-text)] shadow-xl"
        >
          <p id={headingId} className="text-[13px] text-[var(--dash-accent)]">
            {label} · {weekday === 4 ? '목요일' : '수요일'}만 선택
          </p>
          <div className="flex items-center justify-between text-[14px]">
            <button
              data-month-nav
              type="button"
              aria-label="이전 달"
              className="h-8 w-8 rounded hover:bg-[var(--dash-track)]"
              onClick={() => setMonth(shiftMonth(month, -1))}
            >
              ‹
            </button>
            <span aria-live="polite">
              {first.getFullYear()}년 {first.getMonth() + 1}월
            </span>
            <button
              type="button"
              aria-label="다음 달"
              disabled={shiftMonth(month, 1) > monthOf(max)}
              className="h-8 w-8 rounded hover:bg-[var(--dash-track)] disabled:opacity-28"
              onClick={() => setMonth(shiftMonth(month, 1))}
            >
              ›
            </button>
          </div>
          <div
            className="grid grid-cols-7 gap-1 text-center text-[12px] text-[var(--dash-muted)]"
            aria-hidden="true"
          >
            {['일', '월', '화', '수', '목', '금', '토'].map((day) => (
              <span key={day} className="h-5">
                {day}
              </span>
            ))}
          </div>
          <div
            role="group"
            aria-label={`${label} 날짜 선택`}
            className="grid grid-cols-7 gap-x-1 gap-y-3"
          >
            {Array.from({ length: first.getDay() }, (_, index) => (
              <span key={`blank-${index}`} />
            ))}
            {dates.map((date) => {
              const inRange = Boolean(
                rangeStart && rangeEnd && date >= rangeStart && date <= rangeEnd
              )
              const edge = date === value || (inRange && (date === rangeStart || date === rangeEnd))
              return (
                <button
                  key={date}
                  type="button"
                  data-date={date}
                  data-in-range={inRange || undefined}
                  aria-label={date}
                  aria-pressed={date === value}
                  disabled={!allowed(date)}
                  onClick={() => {
                    if (allowed(date)) {
                      onChange(date)
                      close()
                    }
                  }}
                  onKeyDown={(event) => {
                    const delta = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -7, ArrowDown: 7 }[
                      event.key
                    ]
                    if (delta === undefined) return
                    event.preventDefault()
                    const next = dateAt(date)
                    next.setDate(next.getDate() + delta)
                    const nextDate = localDate(next)
                    if (!allowed(nextDate)) return
                    if (monthOf(nextDate) !== month) {
                      pendingFocus.current = nextDate
                      setMonth(monthOf(nextDate))
                    } else
                      root.current
                        ?.querySelector<HTMLButtonElement>(`button[data-date="${nextDate}"]`)
                        ?.focus()
                  }}
                  className={`h-9 min-w-0 rounded-lg text-[13px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--dash-accent)] ${edge ? 'bg-[var(--dash-accent)] text-[var(--dash-button-text)]' : inRange ? 'bg-[var(--dash-accent)]/14 text-[var(--dash-accent)]' : allowed(date) ? 'text-[var(--dash-text)] hover:bg-[var(--dash-track)]' : 'text-[var(--dash-muted)] opacity-28'}`}
                >
                  {Number(date.slice(-2))}
                </button>
              )
            })}
          </div>
          <p className="mt-1 text-[11px] text-[var(--dash-muted)]">
            {weekday === 4
              ? '목요일을 선택해 조회 시작일 지정'
              : `선택 범위 ${rangeStart?.slice(5).replace('-', '/') ?? ''}~${rangeEnd?.slice(5).replace('-', '/') ?? ''} · 종료는 수요일만`}
          </p>
        </div>
      )}
    </div>
  )
}
