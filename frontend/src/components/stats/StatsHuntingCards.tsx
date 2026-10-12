import { formatDuration } from '../hunting/format'
import { formatDecimal, formatEok } from './format'
import type { StatsHuntingSummary } from '../../types'

function MetricCard({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <div className="flex min-w-[240px] flex-1 flex-col gap-[18px] rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[21px] whitespace-nowrap backdrop-blur-[12px]">
      <span className="text-[13px] leading-[1.2] text-[var(--dash-muted)]">{label}</span>
      <span className="text-[30px] leading-9 font-semibold [font-family:Inter,sans-serif]">{value}</span>
      <span className="text-[12px] leading-[1.2] text-[var(--dash-muted)]">{unit}</span>
    </div>
  )
}

export default function StatsHuntingCards({ summary }: { summary?: StatsHuntingSummary }) {
  const days = summary?.dailyRecords.length ?? 0
  const totalMeso = summary?.totalMeso ?? 0
  const totalSolErda = summary?.totalSolErda ?? 0
  const totalMinutes = (summary?.dailyRecords ?? []).reduce((sum, record) => sum + (record.playDurationMin ?? 0), 0)
  const average = (value: number) => (days === 0 ? 0 : value / days)
  const averageMinutes = Math.round(average(totalMinutes))
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap gap-5">
        <MetricCard label="총 메소" value={formatEok(totalMeso)} unit="억 메소" />
        <MetricCard label="총 솔 에르다 조각" value={totalSolErda.toLocaleString('ko-KR')} unit="개" />
        <MetricCard
          label="총 사냥 시간"
          value={formatDuration(totalMinutes)}
          unit={`총 ${totalMinutes.toLocaleString('ko-KR')}분`}
        />
      </div>
      <div className="flex flex-wrap gap-5">
        <MetricCard label="일 평균 메소" value={formatEok(average(totalMeso), 2)} unit="억 메소" />
        <MetricCard label="일 평균 솔 에르다 조각" value={formatDecimal(average(totalSolErda))} unit="개" />
        <MetricCard
          label="일 평균 사냥 시간"
          value={formatDuration(averageMinutes)}
          unit={`일 평균 ${averageMinutes.toLocaleString('ko-KR')}분`}
        />
      </div>
    </div>
  )
}
