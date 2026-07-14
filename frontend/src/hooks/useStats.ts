import { useQuery } from '@tanstack/react-query'
import client from '../api/client'
import type {
  ApiResponse,
  StatsBossItem,
  StatsCrystalSummary,
  StatsHuntingSummary,
} from '../types'

interface StatsParams {
  characterId?: number | null
  dateFrom?: string
  dateTo?: string
}

function buildQs(params: StatsParams): string {
  const q: Record<string, string> = {}
  if (params.characterId) q.characterId = String(params.characterId)
  if (params.dateFrom) q.dateFrom = params.dateFrom
  if (params.dateTo) q.dateTo = params.dateTo
  const qs = new URLSearchParams(q).toString()
  return qs ? `?${qs}` : ''
}

export function useStatsHunting(params: StatsParams) {
  return useQuery({
    queryKey: ['stats/hunting', params],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<StatsHuntingSummary>>(
        `/stats/hunting${buildQs(params)}`,
      )
      return data.data
    },
  })
}

export function useStatsCrystal(params: StatsParams) {
  return useQuery({
    queryKey: ['stats/crystal', params],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<StatsCrystalSummary>>(
        `/stats/crystal${buildQs(params)}`,
      )
      return data.data
    },
  })
}

export function useStatsBossItems(params: StatsParams) {
  return useQuery({
    queryKey: ['stats/boss-items', params],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<StatsBossItem[]>>(
        `/stats/boss-items${buildQs(params)}`,
      )
      return data.data
    },
  })
}
