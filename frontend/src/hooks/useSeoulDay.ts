import { useSyncExternalStore } from 'react'
import { seoulDate } from '../utils/date'

function subscribe(onChange: () => void) {
  let timer: ReturnType<typeof setTimeout>
  const schedule = () => {
    const midnight = new Date(`${seoulDate()}T00:00:00+09:00`).getTime() + 86_400_000
    timer = setTimeout(() => { onChange(); schedule() }, Math.max(1, midnight - Date.now()))
  }
  const refresh = () => { clearTimeout(timer); onChange(); schedule() }
  schedule()
  window.addEventListener('focus', refresh)
  document.addEventListener('visibilitychange', refresh)
  return () => {
    clearTimeout(timer)
    window.removeEventListener('focus', refresh)
    document.removeEventListener('visibilitychange', refresh)
  }
}

/** 열린 화면과 절전 복귀 시에도 서울 자정에 잠금 정보 갱신. */
export function useSeoulDay() {
  return useSyncExternalStore(subscribe, seoulDate, seoulDate)
}
