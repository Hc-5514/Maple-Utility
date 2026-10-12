import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import HuntingFilters from '../components/hunting/HuntingFilters'
import HuntingSummaryCards from '../components/hunting/HuntingSummaryCards'
import HuntingTable from '../components/hunting/HuntingTable'
import DeleteHuntingDialog from '../components/hunting/DeleteHuntingDialog'
import { huntingAssets } from '../components/hunting/assets'
import {
  useDeleteHunting,
  useHuntingPage,
  useHuntingSummary,
  type HuntingPageSize,
} from '../hooks/useHunting'
import { useThemeStore } from '../stores/themeStore'
import client from '../api/client'
import type { ApiResponse, Character, HuntingRecord } from '../types'
import { localDate } from '../utils/date'

function getFirstOfMonth(): string {
  const d = new Date()
  d.setDate(1)
  return localDate(d)
}

export default function HuntingPage() {
  const navigate = useNavigate()
  const { theme } = useThemeStore()
  const today = localDate(new Date())

  const [dateFrom, setDateFrom] = useState(getFirstOfMonth())
  const [dateTo, setDateTo] = useState(today)
  const [characterId, setCharacterId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState<HuntingPageSize>(10)
  const [deleting, setDeleting] = useState<HuntingRecord | null>(null)

  const { data: characters } = useQuery({
    queryKey: ['characters', 'favorites'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters/favorites').then((r) => r.data.data),
  })

  const filters = { characterId, dateFrom, dateTo }
  const records = useHuntingPage({ ...filters, page, size })
  const summary = useHuntingSummary(filters)
  const deleteHunting = useDeleteHunting()

  const characterNames = new Map<number, string>(
    (characters ?? []).map((c) => [c.id, c.characterName]),
  )
  const totalPages = records.data?.totalPages ?? 0
  if (records.data && totalPages > 0 && page >= totalPages) setPage(totalPages - 1)

  const status = records.isLoading ? (
    <p role="status" className="py-16 text-center text-[13px] text-[var(--dash-muted)]">
      불러오는 중...
    </p>
  ) : records.isError ? (
    <div role="alert" className="py-16 text-center">
      <p className="text-[13px] text-[#f87171]">데이터를 불러오지 못했습니다.</p>
      <button
        type="button"
        onClick={() => void records.refetch()}
        className="mt-3 min-h-11 text-[13px] text-[var(--dash-accent)]"
      >
        다시 시도
      </button>
    </div>
  ) : records.data?.content.length === 0 ? (
    <p className="border-b border-[var(--dash-border)] py-16 text-center text-[13px] text-[var(--dash-muted)]">
      기간 내 사냥 기록이 없습니다.
    </p>
  ) : null

  return (
    <div className="flex flex-col gap-7">
      <div className="flex flex-col gap-[7px]">
        <p className="text-[11px] leading-[13px] font-semibold text-[var(--dash-accent)] [font-family:Inter,sans-serif]">
          MY MAPLE / HUNTING LOG
        </p>
        <h1 className="text-[34px] leading-[1.25] font-bold">사냥 기록</h1>
        <p className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">
          쌓이는 메소와 솔 에르다 조각, 오늘의 사냥을 기록하세요.
        </p>
      </div>
      <HuntingFilters
        dateFrom={dateFrom}
        dateTo={dateTo}
        today={today}
        characterId={characterId}
        characters={characters ?? []}
        onDateChange={(from, to) => {
          setDateFrom(from)
          setDateTo(to)
          setPage(0)
        }}
        onCharacterChange={(id) => {
          setCharacterId(id)
          setPage(0)
        }}
      />
      <HuntingSummaryCards summary={summary.data} />
      <HuntingTable
        records={records.data?.content ?? []}
        summary={summary.data}
        characterNames={characterNames}
        page={page}
        totalPages={totalPages}
        size={size}
        status={status}
        onSizeChange={(value) => {
          setSize(value)
          setPage(0)
        }}
        onPageChange={setPage}
        onEdit={(id) => navigate(`/hunting/${id}/edit`)}
        onDelete={setDeleting}
      />
      <p className="flex items-center gap-[10px] text-[12px] leading-[1.45] text-[var(--dash-muted)]">
        <img src={huntingAssets[theme].info} alt="" />
        사냥 기록은 직접 입력한 데이터를 기준으로 집계됩니다.
      </p>
      {deleting && (
        <DeleteHuntingDialog
          record={deleting}
          characterName={characterNames.get(deleting.characterId) ?? `#${deleting.characterId}`}
          pending={deleteHunting.isPending}
          onCancel={() => setDeleting(null)}
          onConfirm={() => deleteHunting.mutate(deleting.id, { onSuccess: () => setDeleting(null) })}
        />
      )}
    </div>
  )
}
