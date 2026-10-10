import type { StatsCrystalWeekly } from '../types'
import { localDate, thursdayWeekStart } from './date'

export type CrystalWindow = 4 | 12

function addDays(date: string, days: number) {
  const value = new Date(`${date}T12:00:00`)
  value.setDate(value.getDate() + days)
  return localDate(value)
}

export function crystalWindowRange(today: string, window: CrystalWindow) {
  const currentWeek = thursdayWeekStart(new Date(`${today}T12:00:00`))
  return { dateFrom: addDays(currentWeek, -(window - 1) * 7), dateTo: addDays(currentWeek, 6) }
}

export function crystalChartPeriods(
  weeklyRecords: StatsCrystalWeekly[],
  dateFrom: string,
  window: CrystalWindow,
) {
  const amounts = new Map(weeklyRecords.map((record) => [record.weekStart, record.totalIncome]))
  const groupSize = window === 4 ? 1 : 4
  return Array.from({ length: window / groupSize }, (_, index) => {
    const start = addDays(dateFrom, index * groupSize * 7)
    const end = addDays(start, groupSize * 7 - 1)
    const totalIncome = Array.from({ length: groupSize }, (_, week) =>
      amounts.get(addDays(start, week * 7)) ?? 0).reduce((sum, amount) => sum + amount, 0)
    return { weekStart: `${start} ~ ${end}`, totalIncome, bossDetails: [] }
  })
}
