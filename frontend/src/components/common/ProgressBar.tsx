interface Props {
  value: number
  max?: number
  className?: string
}

export default function ProgressBar({ value, max = 100, className = '' }: Props) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100))
  return (
    <div className={`h-2 w-full overflow-hidden rounded-full bg-[#374151] ${className}`}>
      <div
        className="h-full rounded-full transition-[width] duration-300"
        style={{ width: `${pct}%`, backgroundColor: '#4ade80' }}
      />
    </div>
  )
}
