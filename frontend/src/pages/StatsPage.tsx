import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import DateRangePicker from '../components/common/DateRangePicker'
import HuntingChart from '../components/stats/HuntingChart'
import CrystalChart from '../components/stats/CrystalChart'
import ItemAcquisitionList from '../components/stats/ItemAcquisitionList'
import { useStatsBossItems, useStatsCrystal, useStatsHunting, type BossItemPageSize } from '../hooks/useStats'
import client from '../api/client'
import type { ApiResponse, Character } from '../types'
import { localDate } from '../utils/date'
import { crystalChartPeriods, crystalWindowRange, type CrystalWindow } from '../utils/crystalPeriods'

const today = import.meta.env.VITE_USE_MOCK === 'true' && import.meta.env.VITE_PREVIEW_DATE
  ? import.meta.env.VITE_PREVIEW_DATE
  : localDate(new Date())

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-[#1a1a2e] p-4">
      <p className="text-xs text-white/50">{label}</p>
      <p className="mt-1 text-lg font-bold text-white">{value}</p>
    </div>
  )
}

export default function StatsPage() {
  const [dateFrom, setDateFrom] = useState(`${today.slice(0, 7)}-01`)
  const [dateTo, setDateTo] = useState(today)
  const [characterId, setCharacterId] = useState<number | null>(null)
  const [bossItemCharacterId, setBossItemCharacterId] = useState<number | null>(null)
  const [bossPage, setBossPage] = useState(0)
  const [bossPageSize, setBossPageSize] = useState<BossItemPageSize>(10)
  const [crystalWindow, setCrystalWindow] = useState<CrystalWindow>(4)

  const params = { characterId, dateFrom, dateTo }
  const crystalRange = crystalWindowRange(today, crystalWindow)

  const { data: characters } = useQuery({
    queryKey: ['characters', 'favorites'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters/favorites').then((r) => r.data.data),
  })

  const { data: huntingData, isLoading: loadingHunting, isError: errorHunting } = useStatsHunting(params)
  const { data: crystalData, isLoading: loadingCrystal, isError: errorCrystal } = useStatsCrystal({ characterId, ...crystalRange })
  const crystalPeriods = crystalData ? crystalChartPeriods(crystalData.weeklyRecords, crystalRange.dateFrom, crystalWindow) : []
  const { data: bossItems, isLoading: loadingBossItems, isError: errorBossItems } = useStatsBossItems({
    characterId: bossItemCharacterId, dateFrom, dateTo, page: bossPage, size: bossPageSize,
  })

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
            setBossPage(0)
          }}
        />
        <select
          aria-label="사냥·결정석 캐릭터"
          value={characterId ?? ''}
          onChange={(e) => {
            setCharacterId(e.target.value === '' ? null : Number(e.target.value))
          }}
          className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white"
        >
          <option value="">전체 즐겨찾기 캐릭터</option>
          {(characters ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.characterName}
            </option>
          ))}
        </select>
      </div>

      {/* 사냥 통계 */}
      <section className="space-y-4">
		<h2 className="rounded border border-[#4ade80]/30 bg-[#4ade80]/10 px-3 py-2 text-lg font-semibold text-white">사냥 통계</h2>
        {loadingHunting ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-white/10" />
            ))}
          </div>
        ) : errorHunting ? (
          <p className="text-sm text-[#f87171]">데이터를 불러오지 못했습니다.</p>
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
		<div className="flex flex-wrap items-center justify-between gap-3 rounded border border-[#facc15]/30 bg-[#facc15]/10 px-3 py-2">
          <h2 className="text-lg font-semibold text-white">결정석 수익</h2>
          <label className="flex items-center gap-2 text-sm text-white/80">
            <input type="checkbox" checked={crystalWindow === 12} onChange={(event) => setCrystalWindow(event.target.checked ? 12 : 4)} />
            최근 12주 합계
          </label>
        </div>
        {loadingCrystal ? (
          <div className="grid grid-cols-2 gap-3">
            {[...Array(2)].map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-white/10" />
            ))}
          </div>
        ) : errorCrystal ? (
          <p className="text-sm text-[#f87171]">데이터를 불러오지 못했습니다.</p>
        ) : crystalData ? (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard
                label="총 수익"
                value={`${(crystalData.totalCrystalIncome / 1e8).toFixed(0)}억`}
              />
              <StatCard
                label="주간 평균"
                value={`${(crystalData.totalCrystalIncome / crystalWindow / 1e8).toFixed(0)}억`}
              />
            </div>
            <div className="rounded-xl bg-[#2d2d44] p-5">
              <CrystalChart data={crystalPeriods} />
            </div>
          </>
        ) : (
          <p className="text-sm text-white/40">데이터 없음</p>
        )}
      </section>

      {/* 보스 아이템 획득 이력 */}
      <section className="space-y-4">
		<div className="flex flex-wrap items-center justify-between gap-3 rounded border border-[#60a5fa]/30 bg-[#60a5fa]/10 px-3 py-2">
          <h2 className="text-lg font-semibold text-white">보스 아이템 획득 이력</h2>
          <select
            aria-label="보스 아이템 캐릭터"
            value={bossItemCharacterId ?? ''}
            onChange={(event) => {
              setBossItemCharacterId(event.target.value === '' ? null : Number(event.target.value))
              setBossPage(0)
            }}
            className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white"
          >
            <option value="">전체 즐겨찾기 캐릭터</option>
            {(characters ?? []).map((character) => (
              <option key={character.id} value={character.id}>{character.characterName}</option>
            ))}
          </select>
        </div>
        {loadingBossItems ? (
          <div className="space-y-2">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-white/10" />
            ))}
          </div>
        ) : errorBossItems ? (
          <p className="text-sm text-[#f87171]">데이터를 불러오지 못했습니다.</p>
        ) : (
          <ItemAcquisitionList
            page={bossItems ?? { content: [], totalElements: 0, totalPages: 0, page: bossPage, size: bossPageSize }}
            onPageChange={setBossPage}
            onPageSizeChange={(size) => { setBossPageSize(size); setBossPage(0) }}
          />
        )}
      </section>
    </div>
  )
}
