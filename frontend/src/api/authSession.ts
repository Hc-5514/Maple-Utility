import { CanceledError, isAxiosError } from 'axios'
import type { QueryClient } from '@tanstack/react-query'
import client from './client'
import { advanceSession, getSessionGeneration, isCurrentSession } from './session'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import type { ApiKeyStatusResponse, ApiResponse, AuthLoginResponse, OAuthProvider, User } from '../types'

export async function loadSessionIdentity(queryClient: QueryClient) {
  const generation = getSessionGeneration()
  const user = await queryClient.fetchQuery({
    queryKey: ['auth/me'],
    staleTime: 0,
    queryFn: async () => (await client.get<ApiResponse<User>>('/auth/me')).data.data,
  })
  if (!isCurrentSession(generation)) throw new CanceledError('인증 세션 변경')
  useAuthStore.getState().setUser(user)
  let hasKey = false
  try {
    const status = await queryClient.fetchQuery({
      queryKey: ['api-key/status'],
      staleTime: 0,
      retry: false,
      queryFn: async () => (await client.get<ApiResponse<ApiKeyStatusResponse>>('/api-key/status')).data.data,
    })
    hasKey = status.registered && status.keyStatus === 'ACTIVE'
  } catch {
    // 상태 조회 실패 시 API 키 필요 화면 진입 보류
  }
  if (!isCurrentSession(generation)) throw new CanceledError('인증 세션 변경')
  useAuthStore.getState().setHasApiKey(hasKey)
  return hasKey
}

export async function installAuthSession(
  queryClient: QueryClient,
  auth?: AuthLoginResponse | null,
  provider?: OAuthProvider,
  initialHasKey = false,
) {
  const previousUser = useAuthStore.getState().user
  const generation = advanceSession(auth?.accessToken ?? localStorage.getItem('accessToken'))
  useAuthStore.setState({
    user: auth?.user ? { ...auth.user, oauthProvider: provider ?? previousUser?.oauthProvider ?? 'KAKAO' } : previousUser,
    hasApiKey: initialHasKey,
    isInitializing: true,
    sessionRevision: generation,
  })
  useCharacterStore.getState().setCharacters([])
  await queryClient.cancelQueries()
  if (!isCurrentSession(generation)) throw new CanceledError('인증 세션 변경')
  queryClient.clear()
  try {
    return await loadSessionIdentity(queryClient)
  } catch {
    if (!isCurrentSession(generation)) throw new CanceledError('인증 세션 변경')
    // 등록·로그인 자체 성공 후 조회 실패는 발급 응답의 인증 상태 유지
    return useAuthStore.getState().hasApiKey
  } finally {
    if (isCurrentSession(generation)) useAuthStore.getState().setInitializing(false)
  }
}

export async function clearAuthSession(queryClient: QueryClient) {
  useAuthStore.getState().logout()
  const generation = getSessionGeneration()
  useCharacterStore.getState().setCharacters([])
  await queryClient.cancelQueries()
  if (isCurrentSession(generation)) queryClient.clear()
}

export function apiKeyErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (code === 'DATA_CONTEXT_CONFLICT') return '이 넥슨 계정은 다른 카카오 계정에 연결되어 있습니다.'
    if (code === 'NEXON_ACCOUNT_UNIDENTIFIABLE') return '넥슨 계정을 확인할 수 없습니다. API Key와 계정의 캐릭터 정보를 확인해 주세요.'
  }
  return fallback
}
