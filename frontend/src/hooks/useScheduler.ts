import { useQuery } from '@tanstack/react-query'
import client from '../api/client'
import type {
  ApiResponse,
  BESchedulerSummaryResponse,
  Character,
  CharacterSchedulerSummary,
  SchedulerSummary,
} from '../types'

function transformSummary(
  be: BESchedulerSummaryResponse,
  characters: Character[],
): SchedulerSummary {
  const charMap = new Map(characters.map((c) => [c.id, c]))

  const characterIds = [
    ...new Set([
      ...be.daily.map((r) => r.characterId),
      ...be.weekly.map((r) => r.characterId),
      ...be.weeklyBoss.map((r) => r.characterId),
      ...be.monthlyBoss.map((r) => r.characterId),
    ]),
  ]

  const summaries: CharacterSchedulerSummary[] = characterIds.map((cId) => {
    const char = charMap.get(cId)
    const daily = be.daily.filter((r) => r.characterId === cId)
    const weekly = be.weekly.filter((r) => r.characterId === cId)
    const weeklyBoss = be.weeklyBoss.filter((r) => r.characterId === cId)
    const monthlyBoss = be.monthlyBoss.filter((r) => r.characterId === cId)

    return {
      characterId: cId,
      characterName: char?.characterName ?? daily[0]?.characterName ?? '',
      characterLevel: char?.characterLevel ?? null,
      characterClass: char?.characterClass ?? null,
      characterImage: char?.characterImage ?? null,
      worldName: char?.worldName ?? null,
      daily: {
        completed: daily.reduce((s, r) => s + r.completedCount, 0),
        total: daily.reduce((s, r) => s + r.totalCount, 0),
      },
      weekly: {
        completed: weekly.filter((r) => r.completed).length,
        total: weekly.length,
      },
      weeklyBoss: {
        completed: weeklyBoss.filter((r) => r.completed).length,
        total: weeklyBoss.length,
      },
      monthlyBoss: {
        completed: monthlyBoss.filter((r) => r.completed).length,
        total: monthlyBoss.length,
      },
    }
  })

  const allSyncedAts = [
    ...be.daily.map((r) => r.syncedAt),
    ...be.weekly.map((r) => r.syncedAt),
  ].filter(Boolean) as string[]

  return {
    characters: summaries,
    syncedAt:
      allSyncedAts.length > 0
        ? allSyncedAts[allSyncedAts.length - 1]
        : new Date().toISOString(),
  }
}

export function useSchedulerSummary() {
  return useQuery({
    queryKey: ['scheduler/summary'],
    queryFn: async () => {
      const [summaryRes, charsRes] = await Promise.all([
        client.get<ApiResponse<BESchedulerSummaryResponse>>('/scheduler/summary'),
        client.get<ApiResponse<Character[]>>('/characters'),
      ])
      return transformSummary(summaryRes.data.data, charsRes.data.data)
    },
    staleTime: 1000 * 60,
  })
}
