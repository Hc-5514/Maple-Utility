import axios, { CanceledError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse, AuthLoginResponse } from '../types'
import { getSessionGeneration, getSessionSignal, isCurrentSession } from './session'

interface RetryableAxiosRequestConfig extends AxiosRequestConfig {
  _retry?: boolean
  _sessionGeneration?: number
}

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
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !authExchange) {
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
