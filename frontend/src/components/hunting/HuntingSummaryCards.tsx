import type { ReactNode } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from './assets'
import { formatKoreanAmount, splitDuration } from './format'
import type { HuntingSummary } from '../../types'

function MetricCard({ label, icon, children }: { label: string; icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex min-w-[240px] flex-1 flex-col gap-[18px] rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[21px] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px]">
      <div className="flex h-9 items-center justify-between">
        <span className="text-[13px] leading-[1.45] text-[var(--dash-muted)]">{label}</span>
        {icon}
      </div>
      <p className="flex items-baseline gap-[7px] leading-[1.45] whitespace-nowrap">{children}</p>
    </div>
  )
}

const Value = ({ children }: { children: ReactNode }) => (
  <span className="text-[30px] [font-family:Inter,sans-serif]">{children}</span>
)
const Unit = ({ children }: { children: ReactNode }) => (
  <span className="text-[13px] text-[var(--dash-muted)]">{children}</span>
)

export default function HuntingSummaryCards({ summary }: { summary?: HuntingSummary }) {
  const { theme } = useThemeStore()
  const assets = huntingAssets[theme]
  const duration = splitDuration(summary?.totalPlayDurationMin ?? 0)
  const meso = summary?.totalMeso ?? 0
  return (
    <div className="flex flex-wrap gap-5">
      <MetricCard label="총 메소" icon={<img src={assets.meso} alt="" className="size-9 object-contain" />}>
        <Value>{meso.toLocaleString('ko-KR')}</Value>
        {formatKoreanAmount(meso) && <Unit>{formatKoreanAmount(meso)}</Unit>}
      </MetricCard>
      <MetricCard
        label="총 솔 에르다 조각"
        icon={<img src={assets.solErda} alt="" className="size-9 object-contain" />}
      >
        <Value>{(summary?.totalSolErda ?? 0).toLocaleString('ko-KR')}</Value>
        <Unit>개</Unit>
      </MetricCard>
      <MetricCard label="총 사냥 시간" icon={<img src={assets.clock} alt="" className="size-[18px]" />}>
        <Value>{duration.hours}</Value>
        <Unit>시간</Unit>
        <Value>{duration.minutes}</Value>
        <Unit>분</Unit>
      </MetricCard>
    </div>
  )
}
