import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { installAuthSession } from '../api/authSession'
import { useAuthStore } from '../stores/authStore'
import type { ApiKeyStatus, ApiResponse, AuthLoginResponse, DataContextItem } from '../types'

export interface DataContextCredential {
  credentialId: number
  keySuffix: string | null
  status: ApiKeyStatus
  lastVerifiedAt: string | null
  current: boolean
}

export interface DataContextGroup {
  contextId: number
  name: string
  detail: string
  image: string | null
  current: boolean
  credentials: DataContextCredential[]
}

// 같은 넥슨 계정 데이터셋(contextId) 단위로 키 목록을 묶고 현재 계정을 맨 앞에 배치
export function groupDataContexts(items: DataContextItem[]): DataContextGroup[] {
  const rowsByContext = new Map<number, DataContextItem[]>()
  for (const item of items)
    rowsByContext.set(item.contextId, [...(rowsByContext.get(item.contextId) ?? []), item])
  const groups = [...rowsByContext.entries()]
    .sort(([a], [b]) => a - b)
    .map(([contextId, rows], index): DataContextGroup => {
      const credentials = [...rows]
        .sort((a, b) => a.credentialId - b.credentialId)
        .map((row) => ({
          credentialId: row.credentialId,
          keySuffix: row.keySuffix,
          status: row.status,
          lastVerifiedAt: row.lastVerifiedAt,
          current: row.current,
        }))
      // 같은 데이터셋의 키는 대표 캐릭터 라벨이 동일
      const label = rows.find((row) => row.representativeCharacterName) ?? rows[0]
      return {
        contextId,
        name: label.representativeCharacterName ?? `넥슨 계정 ${index + 1}`,
        detail: [
          label.characterLevel != null ? `Lv.${label.characterLevel}` : null,
          label.characterClass,
          label.worldName,
        ]
          .filter(Boolean)
          .join(' · '),
        image: label.characterImage,
        current: credentials.some((credential) => credential.current),
        credentials,
      }
    })
  return groups.sort((a, b) => Number(b.current) - Number(a.current))
}

export function useDataContexts() {
  const contexts = useQuery({
    queryKey: ['auth/data-contexts'],
    staleTime: 0,
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<DataContextItem[]>>('/auth/data-contexts')
      return data.data
    },
  })
  const groups = Array.isArray(contexts.data) ? groupDataContexts(contexts.data) : []
  return { contexts, groups }
}

// 전환 성공 시에만 세션을 교체하며, 실패하면 토큰·사용자·캐시를 건드리지 않음
export function useSwitchDataContext() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (credentialId: number) => {
      const { data } = await client.post<ApiResponse<AuthLoginResponse>>('/auth/data-context', {
        credentialId,
      })
      return data.data
    },
    onSuccess: async (auth) => {
      await installAuthSession(queryClient, auth, useAuthStore.getState().user?.oauthProvider)
    },
  })
}
