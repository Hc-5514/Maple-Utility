import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { StatsCrystalWeekly } from '../../types'

interface Props {
  data: StatsCrystalWeekly[]
}

export default function CrystalChart({ data }: Props) {
  if (data.length === 0) {
    return (
      <div className="flex h-60 items-center justify-center text-sm text-white/40">
        데이터 없음
      </div>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis
          dataKey="weekStart"
          tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }}
          tickLine={false}
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
            return [`${n.toLocaleString()} 메소`, '결정석 수익']
          }}
        />
        <Bar dataKey="totalIncome" fill="#4ade80" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
