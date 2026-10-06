import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { useCharacterStore } from '../stores/characterStore'
import type {
  ApiResponse,
  BossDropItemAcquisitionStatus,
  BossItemAcquisition,
  Character,
  GuildRecord,
  SchedulerBossDetail,
  SchedulerDailyRecord,
  SchedulerWeeklyRecord,
} from '../types'

export function useCharacter(characterId: number) {
  return useQuery({
    queryKey: ['characters', 'favorites', characterId],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<Character[]>>('/characters/favorites')
      return data.data.find((c) => c.id === characterId) ?? null
    },
    enabled: characterId > 0,
  })
}

export function useCharacterDaily(characterId: number, date: string) {
  return useQuery({
    queryKey: ['scheduler/daily', characterId, date],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerDailyRecord[]>>(
        `/scheduler/${characterId}/daily?date=${date}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useCharacterWeekly(characterId: number, weekStart: string) {
  return useQuery({
    queryKey: ['scheduler/weekly', characterId, weekStart],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerWeeklyRecord[]>>(
        `/scheduler/${characterId}/weekly?date=${weekStart}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useCharacterBoss(characterId: number, date: string) {
  return useQuery({
    queryKey: ['scheduler/boss', characterId, date],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerBossDetail>>(
        `/scheduler/${characterId}/boss?date=${date}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useCharacterGuild(characterId: number, date: string) {
  return useQuery({
    queryKey: ['scheduler/guild', characterId, date],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<GuildRecord[]>>(
        `/scheduler/${characterId}/guild?date=${date}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useToggleFavorite(characterId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const { data } = await client.patch<ApiResponse<Character>>(
        `/characters/${characterId}/favorite`,
      )
      return data.data
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['characters', 'favorites', characterId] })
      const previous = queryClient.getQueryData<Character | null>(['characters', 'favorites', characterId])
      if (previous) {
        queryClient.setQueryData(['characters', 'favorites', characterId], { ...previous, favorite: !previous.favorite })
        useCharacterStore.getState().updateFavorite(characterId, !previous.favorite)
      }
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['characters', 'favorites', characterId], context.previous)
        useCharacterStore.getState().updateFavorite(characterId, context.previous.favorite)
      }
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['characters', 'favorites', characterId], updated)
      useCharacterStore.getState().updateFavorite(characterId, updated.favorite)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['characters', 'favorites', characterId] })
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
    },
  })
}

export function useBossAcquisitions(characterId: number, bossId: number) {
  return useQuery({
    queryKey: ['boss-acquisitions', characterId, bossId],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<BossDropItemAcquisitionStatus[]>>(
        `/boss/${bossId}/drop-items/acquisitions?characterId=${characterId}`,
      )
      return data.data
    },
    enabled: characterId > 0 && bossId > 0,
  })
}

export function useCreateAcquisition() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: {
      characterId: number
      bossDropItemId: number
      acquiredDate: string
      memo?: string
    }) => {
      const { data } = await client.post<ApiResponse<BossItemAcquisition>>(
        '/boss/item-acquisition',
        body,
      )
      return data.data
    },
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['boss-acquisitions', variables.characterId] })
    },
  })
}

export function useDeleteAcquisition() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id }: { id: number; characterId: number }) => {
      await client.delete(`/boss/item-acquisition/${id}`)
    },
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['boss-acquisitions', variables.characterId] })
    },
  })
}
