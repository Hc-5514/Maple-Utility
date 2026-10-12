import { useQuery } from '@tanstack/react-query'
import client from '../api/client'
import type { ApiResponse, SchedulerSummary } from '../types'

export function useSchedulerSummary(date?: string) {
  return useQuery({
    queryKey: ['scheduler/summary', date],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerSummary>>('/scheduler/summary', {
        params: { date },
      })
      return data.data
    },
    staleTime: 1000 * 60,
  })
}
