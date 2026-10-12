import { crystalAxisMax, type CrystalWindow } from '../../utils/crystalPeriods'
import { formatEok } from './format'

interface Period {
  weekStart: string
  totalIncome: number
}

const PLOT_HEIGHT = 204

export default function CrystalRevenue({
  window,
  periods,
  onWindowChange,
}: {
  window: CrystalWindow
  periods: Period[]
  onWindowChange: (window: CrystalWindow) => void
}) {
  const total = periods.reduce((sum, period) => sum + period.totalIncome, 0)
  const average = periods.length === 0 ? 0 : total / periods.length
  const axisMax = crystalAxisMax(Math.max(0, ...periods.map((period) => period.totalIncome)))
  const ticks = [4, 3, 2, 1, 0].map((index) => (axisMax / 4) * index)
  return (
    <section className="flex flex-col gap-[26px] rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[23px] backdrop-blur-[12px]">
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
              onClick={() => onWindowChange(weeks)}
              className={`flex h-8 items-center rounded-[10px] px-4 text-[14px] leading-[1.2] ${window === weeks ? 'bg-[var(--dash-accent)]/9 text-[var(--dash-accent)]' : 'text-[var(--dash-muted)]'}`}
            >
              최근 {weeks}주
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-12 lg:flex-nowrap">
        <div className="flex w-[265px] shrink-0 flex-col gap-[22px] whitespace-nowrap">
          <span className="text-[13px] leading-[1.2] text-[var(--dash-muted)]">최근 {window}주 총 수익</span>
          <span className="text-[36px] leading-[44px] font-semibold [font-family:Inter,sans-serif]">{formatEok(total)}억 메소</span>
          <span className="h-px bg-[var(--dash-border)]" />
          <span className="text-[13px] leading-[1.2] text-[var(--dash-muted)]">{window === 4 ? '주간 평균' : '4주 평균'}</span>
          <span className="text-[28px] leading-[34px] font-semibold [font-family:Inter,sans-serif]">{formatEok(average)}억 메소</span>
        </div>
        <div className="flex min-w-0 flex-1 gap-5" role="img" aria-label={`최근 ${window}주 결정석 수익 막대 차트`}>
          <div className="flex h-[204px] w-[35px] shrink-0 flex-col justify-between text-[11px] leading-[normal] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">
            {ticks.map((tick) => (
              <span key={tick}>{tick === 0 ? '0' : `${Number(tick.toFixed(2))}억`}</span>
            ))}
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-4">
            <div className="relative h-[204px]">
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
                    <div key={period.weekStart} className="flex h-full flex-1 items-end justify-center">
                      <div
                        title={`${period.weekStart} · ${formatEok(period.totalIncome)}억 메소`}
                        className={`relative rounded-t-lg bg-gradient-to-r ${window === 4 ? 'w-[150px]' : 'w-[200px]'} ${last ? 'from-[var(--dash-accent)] to-[var(--dash-meso)]' : 'from-[var(--dash-accent)]/75 to-[var(--dash-accent)]/38'}`}
                        style={{ height }}
                      />
                    </div>
                  )
                })}
              </div>
            </div>
            <div className="flex text-center text-[12px] leading-[normal] text-[var(--dash-muted)] [font-family:Inter,sans-serif]">
              {periods.map((period) => (
                <span key={period.weekStart} className="flex-1 whitespace-nowrap">
                  {period.weekStart}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
