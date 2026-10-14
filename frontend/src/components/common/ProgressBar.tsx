interface Props {
  value: number
  max?: number
  className?: string
}

export default function ProgressBar({ value, max = 100, className = '' }: Props) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100))
  const done = pct >= 100
  return (
    <div
      role="progressbar"
      aria-valuenow={value}
      aria-valuemin={0}
      aria-valuemax={max}
      className={`h-2 w-full overflow-hidden rounded-full bg-[var(--dash-progress-track)] ${className}`}
    >
      <div
        className={`h-full rounded-full transition-[width] duration-300 ${done ? 'bg-[var(--dash-success)]' : 'bg-[linear-gradient(90deg,var(--dash-accent),var(--dash-progress-end))]'}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  )
}
