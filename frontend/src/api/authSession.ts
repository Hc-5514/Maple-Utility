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

export function dataContextSwitchErrorMessage(error: unknown) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (code === 'DATA_CONTEXT_ACCESS_DENIED') return '선택할 수 없는 계정입니다. 연결된 계정 목록을 새로 불러와 주세요.'
    if (code === 'API_KEY_NOT_REGISTERED') return '등록되지 않은 API Key입니다. 연결된 계정 목록을 새로 불러와 주세요.'
    if (code === 'NEXON_ACCOUNT_UNIDENTIFIABLE') return '넥슨 계정을 확인할 수 없습니다. API Key 상태를 확인해 주세요.'
    if (code === 'API_KEY_INVALID' || code === 'API_KEY_EXPIRED') return '선택한 계정의 API Key가 유효하지 않습니다. API Key를 다시 등록해 주세요.'
  }
  return '계정을 전환하지 못했습니다. 기존 계정과 로그인 상태가 유지됩니다. 잠시 후 다시 시도해 주세요.'
}

export const MAX_NEXON_ACCOUNTS = 5

export function dataContextAddErrorMessage(error: unknown) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (code === 'DATA_CONTEXT_CONFLICT')
      return '이미 다른 카카오 계정에 연결된 넥슨 계정입니다. 해당 카카오 계정에서 연결을 해제한 뒤 다시 시도해 주세요.'
    if (code === 'DATA_CONTEXT_LIMIT_EXCEEDED')
      return `넥슨 계정은 최대 ${MAX_NEXON_ACCOUNTS}개까지 연결할 수 있습니다. 기존 계정의 연결을 해제한 뒤 다시 시도해 주세요.`
    if (code === 'NEXON_ACCOUNT_UNIDENTIFIABLE')
      return '넥슨 계정을 확인할 수 없습니다. API Key와 계정의 캐릭터 정보를 확인해 주세요.'
    if (code === 'API_KEY_INVALID' || code === 'API_KEY_EXPIRED')
      return 'API Key가 유효하지 않습니다. 키를 확인한 뒤 다시 시도해 주세요.'
  }
  return '계정을 추가하지 못했습니다. API Key를 확인한 뒤 다시 시도해 주세요.'
}

export function dataContextUnlinkErrorMessage(error: unknown) {
  if (isAxiosError(error) && error.response?.data?.code === 'DATA_CONTEXT_ACCESS_DENIED')
    return '해제할 수 없는 계정입니다. 연결된 계정 목록을 새로 불러와 주세요.'
  return '연결 해제에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}

export function dataContextDeleteErrorMessage(error: unknown) {
  if (isAxiosError(error)) {
    const code = error.response?.data?.code
    if (code === 'DATA_CONTEXT_SYNC_IN_PROGRESS') return '동기화가 진행 중입니다. 완료 후 다시 시도해 주세요.'
    if (code === 'DATA_CONTEXT_ACCESS_DENIED')
      return '삭제할 수 없는 계정입니다. 연결된 계정 목록을 새로 불러와 주세요.'
  }
  return '데이터 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}

// 경고 문구가 2문장 이상이고 한 문장이 길면(Figma 기준: 행 폭의 절반 초과) 문장 단위로 줄바꿈
const LONG_SENTENCE_LENGTH = 16
export function breakSentences(text: string) {
  const sentences = text.split(/(?<=[.?!])\s+/).filter(Boolean)
  if (sentences.length < 2) return text
  return sentences.some((sentence) => sentence.length > LONG_SENTENCE_LENGTH)
    ? sentences.join('\n')
    : sentences.join(' ')
}
