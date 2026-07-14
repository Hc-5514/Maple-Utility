import { useQuery } from '@tanstack/react-query'
import client from '../api/client'
import type { ApiResponse, SchedulerSummary } from '../types'

export function useSchedulerSummary() {
  return useQuery({
    queryKey: ['scheduler/summary'],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerSummary>>('/scheduler/summary')
      return data.data
    },
    staleTime: 1000 * 60,
  })
}
