import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useWeekStore } from '../stores/weekStore'
import { recentWeeks } from '../utils/date'

// 선택 주: URL ?week= → 공유 스토어 → 이번 주 순으로 결정. 최근 12주 밖의 값은 이번 주로 보정
export function useSelectedWeek() {
  const [params, setParams] = useSearchParams()
  const stored = useWeekStore((state) => state.week)
  const store = useWeekStore((state) => state.setWeek)
  const weeks = recentWeeks()
  const week =
    weeks.find((item) => item.start === params.get('week')) ??
    weeks.find((item) => item.start === stored) ??
    weeks[0]

  // URL로 진입한 주를 공유 스토어에도 반영해 다른 페이지 이동 시 유지
  useEffect(() => {
    if (week.start !== stored) store(week.start)
  }, [week.start, stored, store])

  const setWeek = (start: string) => {
    store(start)
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        if (start === weeks[0].start) next.delete('week')
        else next.set('week', start)
        return next
      },
      { replace: true }
    )
  }

  return { week, weeks, setWeek, search: week.start === weeks[0].start ? '' : `?week=${week.start}` }
}
