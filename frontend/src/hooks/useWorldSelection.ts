import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import type { ApiResponse } from '../types'
export interface WorldSelection {
  worldNames: string[]
}
const queryKey = ['characters/world-selection']
export function useWorldSelection() {
  return useQuery({
    queryKey,
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<WorldSelection>>('/characters/world-selection')
      return data.data
    },
  })
}
export function useSaveWorldSelection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (worldNames: string[]) => {
      const { data } = await client.put<ApiResponse<WorldSelection>>(
        '/characters/world-selection',
        { worldNames }
      )
      return data.data
    },
    onSuccess: (selection) => {
      queryClient.setQueryData(queryKey, selection)
    },
  })
}
