import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse, AuthLoginResponse } from '../types'

interface RetryableAxiosRequestConfig extends AxiosRequestConfig {
  _retry?: boolean
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
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    response.data = normalizeApiResponse(response.data)
    return response
  },
  async (error) => {
    const originalRequest = error.config as RetryableAxiosRequestConfig

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const { data } = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL ?? '/api/v1'}/auth/refresh`,
          {},
          { withCredentials: true },
        )
        const normalized = normalizeApiResponse<AuthLoginResponse>(data)
        const newToken: string = normalized.data.accessToken
        localStorage.setItem('accessToken', newToken)

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return client(originalRequest)
      } catch {
        localStorage.removeItem('accessToken')
        window.location.href = '/login'
      }
    }

    return Promise.reject(error)
  },
)

export default client
