export function formatVerifiedAt(value: string | null) {
  if (!value) return '미인증'
  const date = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : value + '+09:00')
  if (Number.isNaN(date.getTime())) return '미인증'
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type: string) => parts.find((part) => part.type === type)?.value
  return `${get('year')}.${get('month')}.${get('day')} ${get('hour')}:${get('minute')}`
}
