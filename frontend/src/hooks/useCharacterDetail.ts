import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { useCharacterStore } from '../stores/characterStore'
import type {
  ApiResponse,
  BossDropItemAcquisitionStatus,
  BossCandidate,
  BossItemAcquisition,
  BossPeriod,
  Character,
  GuildRecord,
  SchedulerBossDetail,
  SchedulerBossRecord,
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

export function useCharacterBoss(characterId: number, weeklyDate: string, monthlyDate: string) {
  return useQuery({
    queryKey: ['scheduler/boss', characterId, weeklyDate, monthlyDate],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerBossDetail>>(
        `/scheduler/${characterId}/boss?weeklyDate=${weeklyDate}&monthlyDate=${monthlyDate}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useBossCandidates(characterId: number, resetPeriod: 'WEEKLY' | 'MONTHLY' | null) {
  return useQuery({
    queryKey: ['scheduler/boss/candidates', characterId, resetPeriod],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<BossCandidate[]>>(
        `/scheduler/${characterId}/boss/candidates?resetPeriod=${resetPeriod}`,
      )
      return data.data
    },
    enabled: characterId > 0 && resetPeriod !== null,
  })
}

export async function saveManualBossRecords(
  characterId: number,
  body: { periodStart: string; resetPeriod: 'WEEKLY' | 'MONTHLY'; bossIds: number[] },
) {
  const { data } = await client.post<ApiResponse<SchedulerBossRecord[]>>(
    `/scheduler/${characterId}/boss/manual`,
    body,
  )
  return data.data
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
      void queryClient.invalidateQueries({ queryKey: ['hunting'] })
      void queryClient.invalidateQueries({ predicate: query => String(query.queryKey[0]).startsWith('stats/') })
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

export function useBossPeriod(characterId: number, bossId: number, periodStart: string) {
  return useQuery({
    queryKey: ['boss-period', characterId, bossId, periodStart],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<BossPeriod>>(
        `/boss/${bossId}/period?characterId=${characterId}&periodStart=${periodStart}`,
      )
      return data.data
    },
    enabled: characterId > 0 && bossId > 0,
  })
}

export async function saveBossPeriod(period: BossPeriod): Promise<BossPeriod> {
  const { data } = await client.put<ApiResponse<BossPeriod>>(`/boss/${period.bossId}/period`, {
    characterId: period.characterId,
    periodStart: period.periodStart,
    partySize: period.partySize,
    items: period.items.map((item) => ({
      bossDropItemId: item.dropItem.id,
      acquired: item.acquired,
      quantity: item.quantity,
      mesoAmount: item.dropItem.itemKind === 'CRYSTAL' ? item.mesoAmount : null,
    })),
  })
  return data.data
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
