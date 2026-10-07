# 보스 아이템 획득 이력 API

`GET /api/v1/stats/boss-items`의 응답은 목록에서 페이지 객체로 변경.
기존 목록 응답을 사용하는 클라이언트의 `content` 필드 참조 필요.

| 쿼리 | 기본값 | 범위 |
| --- | --- | --- |
| `characterId` | 전체 즐겨찾기 캐릭터 | 본인 소유 즐겨찾기 캐릭터 ID |
| `dateFrom`, `dateTo` | 제한 없음 | `YYYY-MM-DD`, 양 끝 날짜 포함 |
| `page` | `0` | 0 이상의 정수 |
| `size` | `10` | `10`, `20`, `30` |

응답 예시:

```json
{
  "content": [
    {
      "acquiredDate": "2026-10-07",
      "characterName": "꼬농",
      "bossName": "스우",
      "difficulty": "HARD",
      "itemName": "보스 전리품"
    }
  ],
  "totalElements": 21,
  "totalPages": 3,
  "page": 0,
  "size": 10
}
```

API의 페이지 인덱스는 0부터 시작. 사용자 화면의 페이지 번호는 `page + 1`로 표시.
결정석(`itemKind=CRYSTAL`)은 이력과 `totalElements`에서 제외.
결정 데이터의 저장 및 `/api/v1/stats/crystal` 수익 집계는 유지.
잘못된 페이지 번호 또는 허용 범위 밖의 크기는 `INVALID_PAGE_REQUEST`(HTTP 400) 반환.
