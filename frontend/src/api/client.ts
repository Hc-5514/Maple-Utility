import axios, { CanceledError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse, AuthLoginResponse } from '../types'
import { getSessionGeneration, getSessionSignal, isCurrentSession } from './session'

interface RetryableAxiosRequestConfig extends AxiosRequestConfig {
  _retry?: boolean
  _sessionGeneration?: number
}

// 액세스 토큰 자체의 오류 코드(갱신 대상). 코드가 없는 401도 토큰 오류로 취급
const tokenErrorCodes = ['INVALID_TOKEN', 'INVALID_TOKEN_TYPE']

const isApiResponse = <T>(data: unknown): data is ApiResponse<T> => (
  typeof data === 'object' &&
  data !== null &&
  'success' in data &&
  'data' in data
)

const normalizeApiResponse = <T>(data: T | ApiResponse<T>): ApiResponse<T> => {
  if (isApiResponse<T>(data)) {
    return data
  }
  return { success: true, data }
}

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const scoped = config as typeof config & RetryableAxiosRequestConfig
  if (scoped._sessionGeneration !== undefined && !isCurrentSession(scoped._sessionGeneration)) {
    throw new CanceledError('인증 세션 변경')
  }
  scoped._sessionGeneration = getSessionGeneration()
  config.signal = config.signal
    ? AbortSignal.any([config.signal as AbortSignal, getSessionSignal()])
    : getSessionSignal()
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshInFlight: { generation: number; promise: Promise<string> } | null = null

function refreshToken(generation: number) {
  if (refreshInFlight?.generation === generation) return refreshInFlight.promise
  const promise = axios.post(
    `${import.meta.env.VITE_API_BASE_URL ?? '/api/v1'}/auth/refresh`,
    {},
    { withCredentials: true, signal: getSessionSignal() },
  ).then(({ data }) => {
    if (!isCurrentSession(generation)) throw new CanceledError('인증 세션 변경')
    const token = normalizeApiResponse<AuthLoginResponse>(data).data.accessToken
    localStorage.setItem('accessToken', token)
    return token
  }).finally(() => {
    if (refreshInFlight?.generation === generation) refreshInFlight = null
  })
  refreshInFlight = { generation, promise }
  return promise
}

client.interceptors.response.use(
  (response) => {
    const generation = (response.config as RetryableAxiosRequestConfig)._sessionGeneration
    if (generation !== undefined && !isCurrentSession(generation)) {
      throw new CanceledError('인증 세션 변경')
    }
    response.data = normalizeApiResponse(response.data)
    return response
  },
  async (error) => {
    const originalRequest = error.config as RetryableAxiosRequestConfig | undefined
    const generation = originalRequest?._sessionGeneration
    if (generation !== undefined && !isCurrentSession(generation)) {
      return Promise.reject(new CanceledError('인증 세션 변경'))
    }

    const authExchange = ['/auth/kakao', '/auth/nexon-apikey', '/auth/logout', '/auth/refresh']
      .includes(originalRequest?.url ?? '')
    const invalidRegistrationKey = originalRequest?.method?.toLowerCase() === 'post'
      && originalRequest.url === '/api-key'
      && error.response?.data?.code === 'API_KEY_INVALID'
    // 계정 전환의 업무 오류 401은 토큰 문제가 아니므로 갱신 없이 실패 처리해 인증 상태를 보존
    const switchErrorCode = error.response?.data?.code
    const dataContextSwitchRejected = originalRequest?.method?.toLowerCase() === 'post'
      && originalRequest.url === '/auth/data-context'
      && typeof switchErrorCode === 'string'
      && !tokenErrorCodes.includes(switchErrorCode)
    // 연결 해제·데이터 삭제의 업무 오류 401도 토큰 문제가 아님
    const dataContextRemovalRejected = originalRequest?.method?.toLowerCase() === 'delete'
      && (originalRequest.url ?? '').startsWith('/auth/data-contexts/')
      && typeof switchErrorCode === 'string'
      && !tokenErrorCodes.includes(switchErrorCode)
    if (
      error.response?.status === 401 && originalRequest && !originalRequest._retry
      && !authExchange && !invalidRegistrationKey && !dataContextSwitchRejected
      && !dataContextRemovalRejected
    ) {
      originalRequest._retry = true

      try {
        const newToken = await refreshToken(generation ?? getSessionGeneration())

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return client(originalRequest)
      } catch (refreshError) {
        if (generation !== undefined && !isCurrentSession(generation)) {
          return Promise.reject(new CanceledError('인증 세션 변경'))
        }
        if (!axios.isCancel(refreshError)) {
          localStorage.removeItem('accessToken')
          window.location.href = '/login'
        }
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  },
)

export default client
