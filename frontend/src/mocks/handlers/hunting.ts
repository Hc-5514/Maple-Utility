import { http, HttpResponse } from 'msw'

let huntingRecords = [
  { id: 1, characterId: 1, recordDate: '2026-07-14', mesoEarned: 350000000, solErdaEarned: 3, playDurationMin: 120, huntingGround: '아르카나', memo: null, createdAt: '2026-07-14T11:00:00', updatedAt: '2026-07-14T11:00:00' },
  { id: 2, characterId: 1, recordDate: '2026-07-13', mesoEarned: 420000000, solErdaEarned: 5, playDurationMin: 150, huntingGround: '모라스',  memo: null, createdAt: '2026-07-13T11:00:00', updatedAt: '2026-07-13T11:00:00' },
  { id: 3, characterId: 1, recordDate: '2026-07-12', mesoEarned: 380000000, solErdaEarned: 4, playDurationMin: 130, huntingGround: '에스페라', memo: null, createdAt: '2026-07-12T11:00:00', updatedAt: '2026-07-12T11:00:00' },
  { id: 4, characterId: 1, recordDate: '2026-07-11', mesoEarned: 310000000, solErdaEarned: 2, playDurationMin: 100, huntingGround: '아르카나', memo: null, createdAt: '2026-07-11T11:00:00', updatedAt: '2026-07-11T11:00:00' },
  { id: 5, characterId: 1, recordDate: '2026-07-10', mesoEarned: 450000000, solErdaEarned: 6, playDurationMin: 160, huntingGround: '셀라스',  memo: '솔에르다 많이 나옴', createdAt: '2026-07-10T11:00:00', updatedAt: '2026-07-10T11:00:00' },
]
let nextId = huntingRecords.length + 1

export const huntingHandlers = [
  http.get('/api/v1/hunting', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const dateFrom = url.searchParams.get('dateFrom')
    const dateTo = url.searchParams.get('dateTo')
    let records = characterId
      ? huntingRecords.filter(r => r.characterId === characterId)
      : huntingRecords
    if (dateFrom) records = records.filter(r => r.recordDate >= dateFrom)
    if (dateTo)   records = records.filter(r => r.recordDate <= dateTo)
    return HttpResponse.json({ success: true, data: records })
  }),

  http.post('/api/v1/hunting', async ({ request }) => {
    const body = (await request.json()) as Partial<(typeof huntingRecords)[0]>
    const newRecord = {
      id: nextId++,
      characterId: body.characterId ?? 1,
      recordDate: body.recordDate ?? new Date().toISOString().split('T')[0],
      mesoEarned: body.mesoEarned ?? 0,
      solErdaEarned: body.solErdaEarned ?? 0,
      playDurationMin: (body.playDurationMin ?? null) as number | null,
      huntingGround: (body.huntingGround ?? null) as string | null,
      memo: (body.memo ?? null) as string | null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    huntingRecords.push(newRecord as (typeof huntingRecords)[0])
    return HttpResponse.json({ success: true, data: newRecord }, { status: 201 })
  }),

  http.put('/api/v1/hunting/:id', async ({ params, request }) => {
    const body = (await request.json()) as Partial<(typeof huntingRecords)[0]>
    const record = huntingRecords.find(r => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    Object.assign(record, body, { updatedAt: new Date().toISOString() })
    return HttpResponse.json({ success: true, data: record })
  }),

  http.delete('/api/v1/hunting/:id', ({ params }) => {
    huntingRecords = huntingRecords.filter(r => r.id !== Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),
]
