import { http, HttpResponse } from 'msw'
import catalog from '../fixtures/huntingGrounds.json'
import { previewCharacters } from '../fixtures/nexonPreview'
import type { HuntingGround, HuntingRecord } from '../../types'

const groundFavorites = new Set<number>()
const isFavorite = (characterId: number) =>
  previewCharacters.some(character => character.id === characterId && character.favorite)

// [날짜, 캐릭터, 메소, 솔 에르다 조각, 사냥터 ID, 플레이 시간(분), 메모]
const seedRows: [string, number, number, number, number, number, string][] = [
  ['2026-09-30', 2, 230_000_000, 16, 207, 150, '월말 집중 파밍, 조각 다수'],
  ['2026-09-29', 1, 380_000_000, 13, 172, 150, '드롭 장비 · 재획비 사용'],
  ['2026-09-28', 2, 145_000_000, 10, 208, 90, '에르다 조각 · 메소 균형'],
  ['2026-09-27', 1, 200_000_000, 11, 207, 120, '재획비 사용, 드롭 장비 中'],
  ['2026-09-26', 2, 62_000_000, 7, 171, 60, '짧은 사냥, 조각 목적'],
  ['2026-09-25', 1, 140_000_000, 9, 209, 90, '에르다 조각 집중 수급'],
  ['2026-09-24', 2, 110_000_000, 8, 172, 60, '단타 파밍, 조각 위주'],
  ['2026-09-23', 1, 185_000_000, 12, 207, 120, '조각 · 메소 균형 파밍'],
  ['2026-09-22', 2, 250_000_000, 11, 170, 120, '드롭 장비 확인, 재획비 미사용'],
  ['2026-09-21', 1, 120_000_000, 7, 172, 90, '일반 파밍, 특이사항 없음'],
  ['2026-09-20', 2, 175_000_000, 11, 207, 90, '에르다 조각 · 일반 파밍 병행'],
  ['2026-09-19', 1, 430_000_000, 14, 172, 180, '드롭 장비 · 재획비 사용'],
  ['2026-09-18', 2, 88_000_000, 9, 209, 60, '조각 수급, 짧은 파밍'],
  ['2026-09-17', 1, 320_000_000, 15, 171, 150, '재획비 사용, 드롭 장비 多'],
  ['2026-09-16', 2, 155_000_000, 8, 208, 90, '솔 에르다 조각 집중 수급'],
  ['2026-09-15', 1, 210_000_000, 12, 172, 120, '드롭 장비 포함 일반 파밍'],
  ['2026-09-14', 2, 70_000_000, 10, 207, 60, '에르다 조각 수급 위주'],
  ['2026-09-13', 1, 700_000_000, 11, 172, 270, '드롭 장비 · 재획비 사용'],
  ['2026-09-12', 2, 95_000_000, 10, 171, 60, '솔 에르다 위주 단타'],
  ['2026-09-11', 1, 260_000_000, 13, 208, 120, '재획비 사용, 장비 드롭 확인'],
]

const groundById = (id: number | null | undefined) =>
  id == null ? undefined : (catalog as HuntingGround[]).find(ground => ground.id === id)

let huntingRecords: HuntingRecord[] = []
let nextId = 1

export function resetHuntingRecords() {
  huntingRecords = seedRows.map(([recordDate, characterId, mesoEarned, solErdaEarned, groundId, playDurationMin, memo], index) => {
    const ground = groundById(groundId)
    return {
      id: index + 1,
      characterId,
      recordDate,
      mesoEarned,
      solErdaEarned,
      playDurationMin,
      huntingGroundId: ground?.id ?? null,
      regionName: ground?.regionName ?? null,
      huntingGround: ground?.mapName ?? null,
      memo,
      createdAt: `${recordDate}T12:00:00`,
      updatedAt: `${recordDate}T12:00:00`,
    }
  })
  nextId = huntingRecords.length + 1
  groundFavorites.clear()
}
resetHuntingRecords()

export const currentHuntingRecords = () => huntingRecords
export const isFavoriteCharacter = isFavorite

const badRequest = (code: string, message: string) =>
  HttpResponse.json({ success: false, code, message }, { status: 400 })

function filterRecords(url: URL) {
  const characterId = Number(url.searchParams.get('characterId'))
  const from = url.searchParams.get('from')
  const to = url.searchParams.get('to')
  if (characterId && !isFavorite(characterId)) return badRequest('CHARACTER_NOT_FAVORITE', '즐겨찾기 캐릭터 아님')
  if (from && to && from > to) return badRequest('INVALID_DATE_RANGE', '조회 기간 오류')
  return huntingRecords
    .filter(record => isFavorite(record.characterId))
    .filter(record => !characterId || record.characterId === characterId)
    .filter(record => (!from || record.recordDate >= from) && (!to || record.recordDate <= to))
    .sort((a, b) => b.recordDate.localeCompare(a.recordDate) || b.id - a.id)
}

function sortedGrounds(): HuntingGround[] {
  const grounds = (catalog as HuntingGround[]).map(ground => ({ ...ground, favorite: groundFavorites.has(ground.id) }))
  const regionLevel = new Map<string, number>()
  grounds.forEach(ground =>
    regionLevel.set(ground.regionName, Math.max(regionLevel.get(ground.regionName) ?? 0, ground.maxMonsterLevel)))
  return grounds.sort((a, b) =>
    (regionLevel.get(b.regionName) ?? 0) - (regionLevel.get(a.regionName) ?? 0)
    || a.regionName.localeCompare(b.regionName)
    || Number(b.favorite) - Number(a.favorite)
    || b.maxMonsterLevel - a.maxMonsterLevel
    || b.mapName.localeCompare(a.mapName))
}

function groundFields(body: Partial<HuntingRecord>) {
  const ground = groundById(body.huntingGroundId)
  return {
    huntingGroundId: ground?.id ?? null,
    regionName: ground?.regionName ?? null,
    huntingGround: ground?.mapName ?? body.huntingGround ?? null,
  }
}

export const huntingHandlers = [
  http.get('*/api/v1/hunting/grounds', () => HttpResponse.json({ success: true, data: sortedGrounds() })),

  http.put('*/api/v1/hunting/grounds/:id/favorite', ({ params }) => {
    groundFavorites.add(Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),

  http.delete('*/api/v1/hunting/grounds/:id/favorite', ({ params }) => {
    groundFavorites.delete(Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/v1/hunting/latest-ground', ({ request }) => {
    const characterId = Number(new URL(request.url).searchParams.get('characterId'))
    const latest = huntingRecords
      .filter(record => record.characterId === characterId && record.huntingGroundId != null)
      .sort((a, b) => b.recordDate.localeCompare(a.recordDate) || b.id - a.id)[0]
    const ground = groundById(latest?.huntingGroundId)
    return HttpResponse.json({ success: true, data: ground ? { ...ground, favorite: true } : null })
  }),

  http.get('*/api/v1/hunting/page', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 10)
    if (page < 0 || ![10, 20, 30].includes(size)) return badRequest('INVALID_PAGE_REQUEST', '페이지 요청 오류')
    const records = filterRecords(url)
    if (records instanceof HttpResponse) return records
    return HttpResponse.json({
      success: true,
      data: {
        content: records.slice(page * size, page * size + size),
        totalElements: records.length,
        totalPages: Math.ceil(records.length / size),
        page,
        size,
      },
    })
  }),

  http.get('*/api/v1/hunting/summary', ({ request }) => {
    const records = filterRecords(new URL(request.url))
    if (records instanceof HttpResponse) return records
    return HttpResponse.json({
      success: true,
      data: {
        recordCount: records.length,
        totalMeso: records.reduce((sum, record) => sum + record.mesoEarned, 0),
        totalSolErda: records.reduce((sum, record) => sum + record.solErdaEarned, 0),
        totalPlayDurationMin: records.reduce((sum, record) => sum + (record.playDurationMin ?? 0), 0),
      },
    })
  }),

  http.get('*/api/v1/hunting/:id', ({ params }) => {
    const record = huntingRecords.find(r => r.id === Number(params.id))
    if (!record || !isFavorite(record.characterId)) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    return HttpResponse.json({ success: true, data: record })
  }),

  http.get('*/api/v1/hunting', ({ request }) => {
    const records = filterRecords(new URL(request.url))
    if (records instanceof HttpResponse) return records
    return HttpResponse.json({ success: true, data: records })
  }),

  http.post('*/api/v1/hunting', async ({ request }) => {
    const body = (await request.json()) as Partial<HuntingRecord>
    if (!isFavorite(body.characterId ?? 0)) return badRequest('CHARACTER_NOT_FAVORITE', '즐겨찾기 캐릭터 아님')
    const recordDate = body.recordDate ?? new Date().toISOString().split('T')[0]
    if (huntingRecords.some(record => record.characterId === body.characterId && record.recordDate === recordDate)) {
      return HttpResponse.json(
        { success: false, code: 'HUNTING_RECORD_ALREADY_EXISTS', message: '해당 날짜 기록 존재' },
        { status: 409 },
      )
    }
    const newRecord: HuntingRecord = {
      id: nextId++,
      characterId: body.characterId ?? 1,
      recordDate,
      mesoEarned: body.mesoEarned ?? 0,
      solErdaEarned: body.solErdaEarned ?? 0,
      playDurationMin: body.playDurationMin ?? null,
      ...groundFields(body),
      memo: body.memo ?? null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    huntingRecords.push(newRecord)
    return HttpResponse.json({ success: true, data: newRecord }, { status: 201 })
  }),

  http.put('*/api/v1/hunting/:id', async ({ params, request }) => {
    const body = (await request.json()) as Partial<HuntingRecord>
    const record = huntingRecords.find(r => r.id === Number(params.id))
    if (!record || !isFavorite(record.characterId)) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    Object.assign(record, body, groundFields(body), { updatedAt: new Date().toISOString() })
    return HttpResponse.json({ success: true, data: record })
  }),

  http.delete('*/api/v1/hunting/:id', ({ params }) => {
    const record = huntingRecords.find(r => r.id === Number(params.id))
    if (!record || !isFavorite(record.characterId)) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    huntingRecords = huntingRecords.filter(r => r.id !== Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),
]
