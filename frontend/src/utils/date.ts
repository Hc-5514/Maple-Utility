export function localDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function seoulDate(date = new Date()): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function thursdayWeekStart(date: Date): string {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  start.setDate(start.getDate() - ((start.getDay() + 3) % 7))
  return localDate(start)
}

export function monthStart(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`
}

export function recentWeeks(today = new Date(seoulDate() + 'T00:00:00')) {
  const start = new Date(thursdayWeekStart(today) + 'T00:00:00')
  return Array.from({ length: 12 }, (_, index) => {
    const first = new Date(start)
    first.setDate(first.getDate() - index * 7)
    const last = new Date(first)
    last.setDate(last.getDate() + 6)
    return {
      start: localDate(first),
      end: localDate(last),
      date: index === 0 ? localDate(today) : localDate(last),
    }
  })
}

// 선택 주(목~수)가 걸친 월의 시작일 목록. 이번 달 이후의 월은 제외
export function weekMonths(weekStart: string, today = seoulDate()): string[] {
  const start = new Date(`${weekStart}T12:00:00`)
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  const current = `${today.slice(0, 7)}-01`
  return [...new Set([monthStart(start), monthStart(end)])].filter((month) => month <= current)
}

export function monthLabel(month: string): string {
  return `${Number(month.slice(5, 7))}월`
}
