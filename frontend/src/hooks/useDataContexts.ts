import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { installAuthSession } from '../api/authSession'
import { useAuthStore } from '../stores/authStore'
import { clearAuthSession } from '../api/authSession'
import type {
  ApiKeyRegistrationResponse,
  ApiKeyStatus,
  ApiResponse,
  AuthLoginResponse,
  DataContextItem,
  DataContextPrecheckResponse,
} from '../types'

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
  linkedAt: string | null
  current: boolean
  credentials: DataContextCredential[]
}

// 같은 넥슨 계정 데이터셋(contextId) 단위로 키 목록을 묶는다.
// 서버가 현재 사용 중 계정 우선, 이후 연결(등록)순으로 정렬해 내려주므로 순서를 유지하고 현재 계정만 맨 앞으로 보정
export function groupDataContexts(items: DataContextItem[]): DataContextGroup[] {
  const rowsByContext = new Map<number, DataContextItem[]>()
  for (const item of items)
    rowsByContext.set(item.contextId, [...(rowsByContext.get(item.contextId) ?? []), item])
  const groups = [...rowsByContext.entries()]
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
        linkedAt: label.linkedAt ?? null,
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

// 연동 가능 여부만 조회하며 서버 데이터를 바꾸지 않음
export function usePrecheckDataContext() {
  return useMutation({
    mutationFn: async (apiKey: string) => {
      const { data } = await client.post<ApiResponse<DataContextPrecheckResponse>>(
        '/auth/data-contexts/precheck',
        { apiKey }
      )
      return data.data
    },
  })
}

// 카카오 로그인 상태에서 새 넥슨 계정(API Key) 추가. 성공 시 새 계정으로 세션 교체
export function useAddNexonAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (apiKey: string) => {
      const { data } = await client.post<ApiResponse<ApiKeyRegistrationResponse>>('/api-key', { apiKey })
      return data.data
    },
    onSuccess: async (registration) => {
      await installAuthSession(queryClient, registration?.auth, useAuthStore.getState().user?.oauthProvider, true)
      await queryClient.invalidateQueries({ queryKey: ['auth/data-contexts'] })
    },
  })
}

// 카카오 연결만 해제하고 기록 데이터는 보존. 서버가 남은 계정 기준 세션을 재발급
export function useUnlinkDataContext() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (contextId: number) => {
      const { data } = await client.delete<ApiResponse<AuthLoginResponse>>(
        `/auth/data-contexts/${contextId}/link`
      )
      return data.data
    },
    onSuccess: async (auth) => {
      await installAuthSession(queryClient, auth, useAuthStore.getState().user?.oauthProvider)
    },
  })
}

// 계정의 모든 API Key와 기록 데이터를 영구 삭제. API Key 로그인은 204로 로그아웃 처리
export function useDeleteDataContextData() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (contextId: number) => {
      const response = await client.delete<ApiResponse<AuthLoginResponse> | ''>(
        `/auth/data-contexts/${contextId}`
      )
      return response.status === 204 ? null : (response.data as ApiResponse<AuthLoginResponse>).data
    },
    onSuccess: async (auth) => {
      if (auth === null) {
        localStorage.removeItem('accessToken')
        await clearAuthSession(queryClient)
        return
      }
      await installAuthSession(queryClient, auth, useAuthStore.getState().user?.oauthProvider)
    },
  })
}
