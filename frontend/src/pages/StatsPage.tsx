import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import DateRangePicker from '../components/common/DateRangePicker'
import HuntingChart from '../components/stats/HuntingChart'
import CrystalChart from '../components/stats/CrystalChart'
import ItemAcquisitionList from '../components/stats/ItemAcquisitionList'
import { useStatsBossItems, useStatsCrystal, useStatsHunting } from '../hooks/useStats'
import client from '../api/client'
import type { ApiResponse, Character } from '../types'

function getFirstOfMonth(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().split('T')[0]
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-[#1a1a2e] p-4">
      <p className="text-xs text-white/50">{label}</p>
      <p className="mt-1 text-lg font-bold text-white">{value}</p>
    </div>
  )
}

export default function StatsPage() {
  const today = new Date().toISOString().split('T')[0]
  const [dateFrom, setDateFrom] = useState(getFirstOfMonth())
  const [dateTo, setDateTo] = useState(today)
  const [characterId, setCharacterId] = useState<number | null>(null)

  const params = { characterId, dateFrom, dateTo }

  const { data: characters } = useQuery({
    queryKey: ['characters'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters').then((r) => r.data.data),
  })

  const { data: huntingData, isLoading: loadingHunting } = useStatsHunting(params)
  const { data: crystalData, isLoading: loadingCrystal } = useStatsCrystal(params)
  const { data: bossItems, isLoading: loadingBossItems } = useStatsBossItems(params)

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-white">통계</h1>
      </div>

      {/* 필터 */}
      <div className="flex flex-wrap items-center gap-3">
        <DateRangePicker
          startDate={dateFrom}
          endDate={dateTo}
          onChange={(start, end) => {
            setDateFrom(start)
            setDateTo(end)
          }}
        />
        <select
          value={characterId ?? ''}
          onChange={(e) =>
            setCharacterId(e.target.value === '' ? null : Number(e.target.value))
          }
          className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white"
        >
          <option value="">전체 캐릭터</option>
          {(characters ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.characterName}
            </option>
          ))}
        </select>
      </div>

      {/* 사냥 통계 */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-white">사냥 통계</h2>
        {loadingHunting ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-white/10" />
            ))}
          </div>
        ) : huntingData ? (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard label="총 메소" value={`${(huntingData.totalMeso / 1e8).toFixed(1)}억`} />
              <StatCard label="총 솔 에르다" value={`${huntingData.totalSolErda} 개`} />
              <StatCard label="일평균 메소" value={`${(huntingData.avgDailyMeso / 1e8).toFixed(1)}억`} />
              <StatCard label="일평균 솔 에르다" value={`${huntingData.avgDailySolErda} 개`} />
            </div>
            <div className="rounded-xl bg-[#2d2d44] p-5">
              <HuntingChart data={huntingData.dailyRecords} />
            </div>
          </>
        ) : (
          <p className="text-sm text-white/40">데이터 없음</p>
        )}
      </section>

      {/* 결정석 수익 */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-white">결정석 수익</h2>
        {loadingCrystal ? (
          <div className="grid grid-cols-2 gap-3">
            {[...Array(2)].map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-white/10" />
            ))}
          </div>
        ) : crystalData ? (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard
                label="총 수익"
                value={`${(crystalData.totalCrystalIncome / 1e8).toFixed(0)}억`}
              />
              <StatCard
                label="주간 평균"
                value={`${(crystalData.weeklyAverage / 1e8).toFixed(0)}억`}
              />
            </div>
            <div className="rounded-xl bg-[#2d2d44] p-5">
              <CrystalChart data={crystalData.weeklyRecords} />
            </div>
          </>
        ) : (
          <p className="text-sm text-white/40">데이터 없음</p>
        )}
      </section>

      {/* 보스 아이템 획득 이력 */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-white">보스 아이템 획득 이력</h2>
        {loadingBossItems ? (
          <div className="space-y-2">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-white/10" />
            ))}
          </div>
        ) : (
          <ItemAcquisitionList items={bossItems ?? []} />
        )}
      </section>
    </div>
  )
}
