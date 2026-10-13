import { create } from 'zustand'
const KEY = 'maple-selected-week'
function savedWeek(): string | null {
  try {
    return window.sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}
// 대시보드·캐릭터 상세가 공유하는 선택 주(목요일 시작일). URL 쿼리가 없을 때의 기본값
export const useWeekStore = create<{ week: string | null; setWeek: (week: string) => void }>((set) => ({
  week: savedWeek(),
  setWeek: (week) => {
    try {
      window.sessionStorage.setItem(KEY, week)
    } catch {
      /* Storage unavailable; retain in-memory week. */
    }
    set({ week })
  },
}))
