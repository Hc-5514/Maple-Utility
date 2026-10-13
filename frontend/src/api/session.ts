let generation = 0
let controller = new AbortController()

export const getSessionGeneration = () => generation
export const getSessionSignal = () => controller.signal
export const isCurrentSession = (value: number) => value === generation

// 토큰 갱신은 동일 세션, 로그인·데이터셋 전환·로그아웃은 새 세션
export function advanceSession(accessToken: string | null) {
  generation += 1
  controller.abort()
  controller = new AbortController()
  if (accessToken) localStorage.setItem('accessToken', accessToken)
  else localStorage.removeItem('accessToken')
  return generation
}
