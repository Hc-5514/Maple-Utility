import { localDate, thursdayWeekStart } from '../../utils/date'

/** 과거 단일 획득일 시드의 주간·월간 기간 보정. */
export function acquisitionPeriod(acquiredDate: string, bossName: string) {
  const date = new Date(`${acquiredDate}T12:00:00`)
  const periodStart = bossName === '검은 마법사' ? `${acquiredDate.slice(0, 7)}-01` : thursdayWeekStart(date)
  const end = new Date(`${periodStart}T12:00:00`)
  if (bossName === '검은 마법사') end.setMonth(end.getMonth() + 1, 0)
  else end.setDate(end.getDate() + 6)
  return { periodStart, periodEnd: localDate(end), detailItem: null }
}

export const luckyItemNames = new Set([
  '루즈 컨트롤 머신 마크', '마력이 깃든 안대', '몽환의 벨트', '저주받은 마도서 선택 상자',
  '거대한 공포', '커맨더 포스 이어링', '고통의 근원', '창세의 뱃지', '미트라의 분노 선택 상자',
  '컴플리트 언더컨트롤', '혼돈의 칠흑 장신구 상자',
  '불멸의 유산', '굶주리는 핏빛 원혼', '황홀한 악몽', '근원의 속삭임', '죽음의 맹세', '오만의 원죄',
  '의지의 에테르넬 방어구 상자', '고대의 에테르넬 방어구 상자', '흉수의 에테르넬 방어구 상자',
  '광기의 에테르넬 방어구 상자', '환상의 에테르넬 방어구 상자', '욕망의 에테르넬 방어구 상자',
  '맹세의 에테르넬 방어구 상자', '갈망의 에테르넬 방어구 상자',
  '생명의 연마석', '신념의 연마석', '영롱한 달빛 포션',
  '익셉셔널 해머(얼굴장식)', '익셉셔널 해머(눈장식)', '익셉셔널 해머(귀고리)',
  '익셉셔널 해머(벨트)', '익셉셔널 해머(훈장)', '익셉셔널 해머(펜던트)', '익셉셔널 해머(포켓)',
  '1단계 소울 에테르', '2단계 소울 에테르', '3단계 소울 에테르', '4단계 소울 에테르',
  '녹옥의 보스 반지 상자', '홍옥의 보스 반지 상자', '흑옥의 보스 반지 상자', '백옥의 보스 반지 상자', '생명의 보스 반지 상자',
])

export const ringDetails = new Set(['리스트레인트 링 Lv4', '컨티뉴어스링 Lv4'])
export const isLuckyItem = (itemName: string, detailItem: string | null) => {
  // 상세 결과를 병기한 API 이름에서 상자 원래 이름만 비교.
  const name = itemName.split(' (')[0]
  return luckyItemNames.has(name) && (!name.endsWith('보스 반지 상자') || ringDetails.has(detailItem ?? ''))
}
