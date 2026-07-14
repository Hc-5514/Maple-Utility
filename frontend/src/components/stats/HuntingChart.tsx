import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { StatsHuntingDaily } from '../../types'

interface Props {
  data: StatsHuntingDaily[]
}

export default function HuntingChart({ data }: Props) {
  if (data.length === 0) {
    return (
      <div className="flex h-60 items-center justify-center text-sm text-white/40">
        데이터 없음
      </div>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart data={data} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis
          dataKey="date"
          tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }}
          tickLine={false}
          interval="preserveStartEnd"
        />
        <YAxis
          tickFormatter={(v: number) => `${(v / 1e8).toFixed(0)}억`}
          tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          width={44}
        />
        <Tooltip
          contentStyle={{ backgroundColor: '#2d2d44', border: 'none', borderRadius: 8, fontSize: 12 }}
          labelStyle={{ color: 'rgba(255,255,255,0.6)' }}
          itemStyle={{ color: '#4ade80' }}
          formatter={(value) => {
            const n = typeof value === 'number' ? value : 0
            return [`${n.toLocaleString()} 메소`, '메소']
          }}
        />
        <Line
          type="monotone"
          dataKey="mesoEarned"
          stroke="#4ade80"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4, fill: '#4ade80' }}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
