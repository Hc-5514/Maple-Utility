// 백엔드 BossItemDetailOptions와 동일한 상자 상세 옵션 (목 API용)
const RING_BOX = ['리스트레인트 링 Lv4', '컨티뉴어스링 Lv4', '기타']
const CHAOS_BLACK_ACCESSORY_BOX = [
  '루즈 컨트롤 머신 마크', '마력이 깃든 안대', '몽환의 벨트', '저주받은 마도서 선택 상자',
  '거대한 공포', '커맨더 포스 이어링', '고통의 근원',
]

export function detailOptionsFor(itemName: string): string[] {
  if (itemName.endsWith('보스 반지 상자')) return RING_BOX
  if (itemName === '혼돈의 칠흑 장신구 상자') return CHAOS_BLACK_ACCESSORY_BOX
  return []
}
