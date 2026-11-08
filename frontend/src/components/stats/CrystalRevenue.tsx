import { useEffect, useId, useRef, useState } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { crystalAxisMax, type CrystalWindow } from '../../utils/crystalPeriods'
import { formatEok } from './format'

interface Period {
  weekStart: string
  totalIncome: number
  weeklyIncome: number
  monthlyIncome: number
}

const PLOT_HEIGHT = 204
const TOOLTIP_WIDTH = 246

export default function CrystalRevenue({
  window,
  periods,
  onWindowChange,
}: {
  window: CrystalWindow
  periods: Period[]
  onWindowChange: (window: CrystalWindow) => void
}) {
  const { theme } = useThemeStore()
  const tooltipId = useId()
  const chartRef = useRef<HTMLDivElement>(null)
  const tooltipSlotRef = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState<{
    period: Period
    periods: Period[]
    window: CrystalWindow
    left: number
  } | null>(null)
  // Filter/window changes must never retain a tooltip for the old query result.
  const visibleActive = active?.periods === periods && active.window === window ? active : null
  function showPeriod(period: Period, target: HTMLButtonElement) {
    const bar = target.getBoundingClientRect()
    const slot = tooltipSlotRef.current?.getBoundingClientRect()
    if (!slot) return
    const viewportMax = globalThis.innerWidth - TOOLTIP_WIDTH - 8
    const minLeft = slot.width >= TOOLTIP_WIDTH ? Math.max(8, slot.left) : 8
    const maxLeft = slot.width >= TOOLTIP_WIDTH ? Math.min(viewportMax, slot.right - TOOLTIP_WIDTH) : viewportMax
    const left = Math.max(minLeft, Math.min((bar.left + bar.right - TOOLTIP_WIDTH) / 2, maxLeft))
    setActive({ period, periods, window, left: left - slot.left })
  }
  useEffect(() => {
    if (!visibleActive) return
    const close = () => setActive(null)
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !chartRef.current?.contains(event.target)) close()
    }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') close() }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    globalThis.addEventListener('resize', close)
    globalThis.addEventListener('scroll', close, true)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
      globalThis.removeEventListener('resize', close)
      globalThis.removeEventListener('scroll', close, true)
    }
  }, [visibleActive])
  const total = periods.reduce((sum, period) => sum + period.totalIncome, 0)
  const average = periods.length === 0 ? 0 : total / periods.length
  const axisMax = crystalAxisMax(Math.max(0, ...periods.map((period) => period.totalIncome)))
  const ticks = [4, 3, 2, 1, 0].map((index) => (axisMax / 4) * index)
  return (
    <section className="flex flex-col gap-3 rounded-2xl lg:min-h-[501px] border border-[var(--dash-border)] bg-[var(--dash-card)] p-[23px] backdrop-blur-[12px]">
      <div className="flex items-center justify-between">
        <h3 className="text-[16px] leading-[1.2] font-bold">기간별 결정석 수익</h3>
        <div
          role="group"
          aria-label="결정석 집계 기간"
          className="flex gap-1 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] p-1"
        >
          {([4, 12] as const).map((weeks) => (
            <button
              key={weeks}
              type="button"
              aria-pressed={window === weeks}
              onClick={() => { setActive(null); onWindowChange(weeks) }}
              className={`flex h-8 items-center rounded-[10px] px-4 text-[14px] leading-[1.2] ${window === weeks ? 'bg-[var(--dash-accent)]/9 text-[var(--dash-accent)]' : 'text-[var(--dash-muted)]'}`}
            >
              최근 {weeks}주
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-[14px]">
        <div className="grid grid-cols-1 gap-x-12 gap-y-3 lg:grid-cols-[265px_minmax(0,1fr)]">
          <div className="flex w-full shrink-0 flex-col gap-[22px] whitespace-nowrap lg:col-start-1 lg:row-start-2 lg:self-center lg:w-[265px]">
            <span className="text-[13px] leading-[1.2] text-[var(--dash-muted)]">최근 {window}주 총 수익</span>
            <span className="text-[36px] leading-[44px] font-semibold [font-family:Inter,sans-serif]">{formatEok(total)}억 메소</span>
            <span className="h-px bg-[var(--dash-border)]" />
            <span className="text-[13px] leading-[1.2] text-[var(--dash-muted)]">{window === 4 ? '주간 평균' : '4주 평균'}</span>
            <span className="text-[28px] leading-[34px] font-semibold [font-family:Inter,sans-serif]">{formatEok(average)}억 메소</span>
          </div>
          <div ref={tooltipSlotRef} data-testid="crystal-tooltip-slot" className="pointer-events-none relative h-[124px] shrink-0 lg:col-span-2 lg:row-start-1">
            {visibleActive && (
              <div
                id={tooltipId}
                role="tooltip"
                data-theme={theme}
                className={`dashboard-theme absolute top-0 z-10 flex h-[124px] w-[246px] flex-col gap-[10px] rounded-xl p-4 text-[var(--dash-text)] shadow-[var(--dash-popover-shadow)] ring-1 ring-inset ring-[var(--dash-border)] ${theme === 'light' ? 'bg-white' : 'bg-[var(--dash-popover)]'}`}
                style={{ left: visibleActive.left }}
              >
                <p className="text-[12px] leading-[1.2] font-bold whitespace-nowrap">{visibleActive.period.weekStart}</p>
                {([
                  ['총합', visibleActive.period.totalIncome],
                  ['주간 결정', visibleActive.period.weeklyIncome],
                  ['월간 결정', visibleActive.period.monthlyIncome],
                ] as const).map(([label, amount]) => (
                  <div key={label} className="grid grid-cols-[56px_110px_28px] items-center gap-[10px] text-[13px] leading-[1.2] font-bold whitespace-nowrap">
                    <span>{label}</span><span className="tabular-nums">{amount.toLocaleString('ko-KR')}</span><span>메소</span>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="flex w-full min-w-0 gap-3 sm:gap-5 lg:col-start-2 lg:row-start-2" ref={chartRef} role="group" aria-label={`최근 ${window}주 결정석 수익 막대 차트`}>
            <div className="flex h-[204px] w-[35px] shrink-0 flex-col justify-between text-[11px] leading-[normal] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">
              {ticks.map((tick) => (
                <span key={tick}>{tick === 0 ? '0' : `${Number(tick.toFixed(2))}억`}</span>
              ))}
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-4">
              <div data-testid="crystal-plot" className="relative h-[204px]">
                {[0, 1, 2, 3, 4].map((index) => (
                  <span
                    key={index}
                    className="absolute inset-x-0 h-px bg-[var(--dash-border)]"
                    style={{ top: Math.min(PLOT_HEIGHT - 1, Math.round((PLOT_HEIGHT / 4) * index)) }}
                  />
                ))}
                <div className="absolute inset-0 flex items-end">
                  {periods.map((period, index) => {
                    const last = index === periods.length - 1
                    const height = Math.round((period.totalIncome / 100_000_000 / axisMax) * PLOT_HEIGHT)
                    return (
                      <button
                        key={period.weekStart}
                        type="button"
                        aria-label={`${period.weekStart} 결정석 수익: 총합 ${period.totalIncome.toLocaleString('ko-KR')} 메소, 주간 결정 ${period.weeklyIncome.toLocaleString('ko-KR')} 메소, 월간 결정 ${period.monthlyIncome.toLocaleString('ko-KR')} 메소`}
                        aria-describedby={visibleActive?.period === period ? tooltipId : undefined}
                        onMouseEnter={(event) => showPeriod(period, event.currentTarget)}
                        onMouseLeave={(event) => {
                          if (document.activeElement !== event.currentTarget) setActive(null)
                        }}
                        onFocus={(event) => showPeriod(period, event.currentTarget)}
                        onBlur={() => setActive(null)}
                        onClick={(event) => showPeriod(period, event.currentTarget)}
                        className="flex h-full min-w-0 flex-1 cursor-pointer items-end justify-center rounded-lg px-1 outline-none focus-visible:ring-2 focus-visible:ring-[var(--dash-accent)] focus-visible:ring-offset-2"
                      >
                        <span
                          aria-hidden="true"
                          className={`block rounded-t-lg bg-gradient-to-r w-full ${window === 4 ? 'max-w-[150px]' : 'max-w-[200px]'} ${last ? 'from-[var(--dash-accent)] to-[var(--dash-meso)]' : 'from-[var(--dash-accent)]/75 to-[var(--dash-accent)]/38'}`}
                          style={{ height }}
                        />
                      </button>
                    )
                  })}
                </div>
              </div>
              <div className="flex text-center leading-[1.3] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">
                {periods.map((period) => (
                  <span key={period.weekStart} className="min-w-0 flex-1 px-1 text-[11px] break-keep sm:text-[12px]">
                    {period.weekStart}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
        <p className="text-[12px] leading-[1.2] text-[var(--dash-muted)]">
          월간 보스 수익은 매월 1일이 속한 주(목요일 시작)에 합산됩니다.
        </p>
      </div>
    </section>
  )
}
