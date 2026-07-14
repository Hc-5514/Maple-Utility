import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import type {
  ApiResponse,
  BossMaster,
  Character,
  GuildRecord,
  SchedulerBossRecord,
  SchedulerDailyRecord,
  SchedulerWeeklyRecord,
} from '../types'

export function useCharacter(characterId: number) {
  return useQuery({
    queryKey: ['characters', characterId],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<Character>>(`/characters/${characterId}`)
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useCharacterDaily(characterId: number, date: string) {
  return useQuery({
    queryKey: ['scheduler/daily', characterId, date],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SchedulerDailyRecord[]>>(
        `/scheduler/daily?characterId=${characterId}&date=${date}`,
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
        `/scheduler/weekly?characterId=${characterId}&weekStart=${weekStart}`,
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
      const { data } = await client.get<ApiResponse<SchedulerBossRecord[]>>(
        `/scheduler/boss?characterId=${characterId}&date=${date}`,
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
        `/scheduler/guild?characterId=${characterId}&date=${date}`,
      )
      return data.data
    },
    enabled: characterId > 0,
  })
}

export function useBossMasters() {
  return useQuery({
    queryKey: ['bosses'],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<BossMaster[]>>('/bosses')
      return data.data
    },
    staleTime: Infinity,
  })
}

export function useToggleBoss() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, isCompleted }: { id: number; isCompleted: boolean }) => {
      const { data } = await client.put<ApiResponse<SchedulerBossRecord>>(
        `/scheduler/boss/${id}`,
        { isCompleted },
      )
      return data.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['scheduler/boss'] })
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
    },
  })
}

export function useToggleFavorite(characterId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const { data } = await client.put<ApiResponse<Character>>(
        `/characters/${characterId}/favorite`,
      )
      return data.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['characters', characterId] })
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
    },
  })
}
