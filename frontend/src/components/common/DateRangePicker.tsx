interface Props {
  startDate: string
  endDate: string
  onChange: (start: string, end: string) => void
}

export default function DateRangePicker({ startDate, endDate, onChange }: Props) {
  return (
    <div className="flex items-center gap-2">
      <input
        type="date"
        value={startDate}
        max={endDate}
        onChange={(e) => onChange(e.target.value, endDate)}
        className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white [color-scheme:dark]"
      />
      <span className="text-sm text-white/50">~</span>
      <input
        type="date"
        value={endDate}
        min={startDate}
        onChange={(e) => onChange(startDate, e.target.value)}
        className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white [color-scheme:dark]"
      />
    </div>
  )
}
