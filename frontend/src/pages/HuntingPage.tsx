import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import DateRangePicker from '../components/common/DateRangePicker'
import HuntingTable from '../components/hunting/HuntingTable'
import { useDeleteHunting, useHuntingList } from '../hooks/useHunting'
import client from '../api/client'
import type { ApiResponse, Character } from '../types'

function getFirstOfMonth(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().split('T')[0]
}

export default function HuntingPage() {
  const navigate = useNavigate()
  const today = new Date().toISOString().split('T')[0]

  const [dateFrom, setDateFrom] = useState(getFirstOfMonth())
  const [dateTo, setDateTo] = useState(today)
  const [characterId, setCharacterId] = useState<number | null>(null)

  const { data: characters } = useQuery({
    queryKey: ['characters'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters').then((r) => r.data.data),
  })

  const { data: records, isLoading } = useHuntingList({ characterId, dateFrom, dateTo })
  const deleteHunting = useDeleteHunting()

  const characterMap = new Map<number, string>(
    (characters ?? []).map((c) => [c.id, c.characterName]),
  )

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-white">사냥 기록</h1>
        <Link
          to="/hunting/new"
          className="rounded-lg bg-[#4ade80] px-4 py-2 text-sm font-semibold text-[#1a1a2e] hover:opacity-80"
        >
          + 기록 추가
        </Link>
      </div>

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

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-12 animate-pulse rounded-lg bg-white/10" />
          ))}
        </div>
      ) : (
        <HuntingTable
          records={records ?? []}
          characterMap={characterMap}
          onEdit={(id) => navigate(`/hunting/${id}/edit`)}
          onDelete={(id) => deleteHunting.mutate(id)}
        />
      )}
    </div>
  )
}
