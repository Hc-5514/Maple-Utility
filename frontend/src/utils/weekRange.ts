import { localDate, thursdayWeekStart } from './date'

/** 서울 날짜 문자열을 기준으로 목요일~수요일 조회 범위 계산. */
export function currentWeekRange(today: string) {
  const dateFrom = thursdayWeekStart(new Date(`${today}T12:00:00`))
  const end = new Date(`${dateFrom}T12:00:00`)
  end.setDate(end.getDate() + 6)
  return { dateFrom, dateTo: localDate(end) }
}
