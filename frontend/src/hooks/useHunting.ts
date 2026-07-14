import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import type { ApiResponse, HuntingRecord } from '../types'

interface HuntingListParams {
  characterId?: number | null
  dateFrom?: string
  dateTo?: string
}

interface HuntingFormData {
  characterId: number
  recordDate: string
  mesoEarned: number
  solErdaEarned: number
  playDurationMin?: number | null
  huntingGround?: string | null
  memo?: string | null
}

export function useHuntingList(params: HuntingListParams) {
  const query: Record<string, string> = {}
  if (params.characterId) query.characterId = String(params.characterId)
  if (params.dateFrom) query.dateFrom = params.dateFrom
  if (params.dateTo) query.dateTo = params.dateTo
  const qs = new URLSearchParams(query).toString()

  return useQuery({
    queryKey: ['hunting', params],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<HuntingRecord[]>>(
        `/hunting${qs ? `?${qs}` : ''}`,
      )
      return data.data
    },
  })
}

export function useHuntingRecord(id: number | undefined) {
  return useQuery({
    queryKey: ['hunting', id],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<HuntingRecord>>(`/hunting/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useCreateHunting() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: HuntingFormData) => {
      const { data } = await client.post<ApiResponse<HuntingRecord>>('/hunting', body)
      return data.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hunting'] })
    },
  })
}

export function useUpdateHunting() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, ...body }: HuntingFormData & { id: number }) => {
      const { data } = await client.put<ApiResponse<HuntingRecord>>(`/hunting/${id}`, body)
      return data.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hunting'] })
    },
  })
}

export function useDeleteHunting() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await client.delete(`/hunting/${id}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hunting'] })
    },
  })
}
