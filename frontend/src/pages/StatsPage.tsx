import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import PageHeading from '../components/common/PageHeading'
import HuntingFilters from '../components/hunting/HuntingFilters'
import CrystalRevenue from '../components/stats/CrystalRevenue'
import ItemAcquisitionList from '../components/stats/ItemAcquisitionList'
import StatsHuntingCards from '../components/stats/StatsHuntingCards'
import {
  useStatsBosses,
  useStatsBossItems,
  useStatsCrystal,
  useStatsHunting,
  type BossItemPageSize,
} from '../hooks/useStats'
import client from '../api/client'
import type { ApiResponse, Character } from '../types'
import { localDate } from '../utils/date'
import { crystalChartPeriods, crystalWindowRange, type CrystalWindow } from '../utils/crystalPeriods'

const today = import.meta.env.VITE_USE_MOCK === 'true' && import.meta.env.VITE_PREVIEW_DATE
  ? import.meta.env.VITE_PREVIEW_DATE
  : localDate(new Date())

function SectionStatus({ loading, error }: { loading: boolean; error: boolean }) {
  if (loading) {
    return (
      <p role="status" className="py-10 text-center text-[13px] text-[var(--dash-muted)]">
        불러오는 중...
      </p>
    )
  }
  if (error) {
    return (
      <p role="alert" className="py-10 text-center text-[13px] text-[#f87171]">
        데이터를 불러오지 못했습니다.
      </p>
    )
  }
  return null
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <h2 className="text-[20px] leading-[1.2] font-bold">{title}</h2>
      {children}
    </>
  )
}

export default function StatsPage() {
  const [dateFrom, setDateFrom] = useState(`${today.slice(0, 7)}-01`)
  const [dateTo, setDateTo] = useState(today)
  const [characterId, setCharacterId] = useState<number | null>(null)
  const [bossName, setBossName] = useState<string | null>(null)
  const [bossPage, setBossPage] = useState(0)
  const [bossPageSize, setBossPageSize] = useState<BossItemPageSize>(10)
  const [crystalWindow, setCrystalWindow] = useState<CrystalWindow>(4)

  const crystalRange = crystalWindowRange(dateTo, crystalWindow)

  const { data: characters } = useQuery({
    queryKey: ['characters', 'favorites'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters/favorites').then((r) => r.data.data),
  })
  const hunting = useStatsHunting({ characterId, dateFrom, dateTo })
  const crystal = useStatsCrystal({ characterId, ...crystalRange })
  const bosses = useStatsBosses()
  const bossItems = useStatsBossItems({
    characterId, bossName, dateFrom, dateTo, page: bossPage, size: bossPageSize,
  })
  const crystalPeriods = crystalChartPeriods(crystal.data?.weeklyRecords ?? [], crystalRange.dateFrom, crystalWindow)

  return (
    <div className="flex flex-col gap-7">
      <PageHeading
        eyebrow="MY MAPLE / ANALYTICS"
        title="쌓인 기록, 보이는 성장."
        description="사냥 수익과 결정석, 보스 아이템 획득을 한곳에서 확인하세요."
      />
      <HuntingFilters
        dateFrom={dateFrom}
        dateTo={dateTo}
        today={today}
        characterId={characterId}
        characters={characters ?? []}
        showAddButton={false}
        onDateChange={(from, to) => {
          setDateFrom(from)
          setDateTo(to)
          setBossPage(0)
        }}
        onCharacterChange={(id) => {
          setCharacterId(id)
          setBossPage(0)
        }}
      />

      <Section title="사냥 통계">
        <SectionStatus loading={hunting.isLoading} error={hunting.isError} />
        {hunting.data && <StatsHuntingCards summary={hunting.data} />}
      </Section>

      <Section title="결정석 수익">
        <SectionStatus loading={crystal.isLoading} error={crystal.isError} />
        {crystal.data && (
          <CrystalRevenue window={crystalWindow} periods={crystalPeriods} onWindowChange={setCrystalWindow} />
        )}
      </Section>

      <Section title="보스 아이템 획득 이력">
        <SectionStatus loading={bossItems.isLoading} error={bossItems.isError} />
        {bossItems.data && (
          <ItemAcquisitionList
            page={bossItems.data}
            bossName={bossName}
            bosses={bosses.data ?? []}
            onBossChange={(name) => {
              setBossName(name)
              setBossPage(0)
            }}
            onPageChange={setBossPage}
            onPageSizeChange={(size) => {
              setBossPageSize(size)
              setBossPage(0)
            }}
          />
        )}
      </Section>
    </div>
  )
}
