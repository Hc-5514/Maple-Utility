export function formatKoreanAmount(value: number): string {
  const eok = Math.floor(value / 100_000_000)
  const man = Math.floor((value % 100_000_000) / 10_000)
  return [eok > 0 ? `${eok}억` : '', man > 0 ? `${man}만` : ''].filter(Boolean).join(' ')
}

export function splitDuration(minutes: number): { hours: number; minutes: number } {
  return { hours: Math.floor(minutes / 60), minutes: minutes % 60 }
}

export function formatDuration(minutes: number | null): string {
  if (minutes == null) return '-'
  const parts = splitDuration(minutes)
  if (parts.hours === 0) return `${parts.minutes}분`
  return parts.minutes === 0 ? `${parts.hours}시간` : `${parts.hours}시간 ${parts.minutes}분`
}

export function formatDotDate(date: string): string {
  return date.replace(/-/g, '. ')
}
