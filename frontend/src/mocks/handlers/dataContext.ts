import { http, HttpResponse } from 'msw'
import type { DataContextItem } from '../../types'
import { settingsAssets } from '../../components/settings/assets'
import { mockAuthToken } from './auth'

// 같은 넥슨 계정(contextId 1)의 키 2개와 다른 넥슨 계정(contextId 2)의 키 1개
const contexts: DataContextItem[] = [
  {
    contextId: 1,
    ownerUserId: 1,
    credentialId: 10,
    keySuffix: '9f2a',
    status: 'ACTIVE',
    lastVerifiedAt: '2026-09-17T09:42:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
    characterImage: settingsAssets.light.imgCharacterPortrait,
  },
  {
    contextId: 1,
    ownerUserId: 1,
    credentialId: 11,
    keySuffix: '1b3c',
    status: 'EXPIRED',
    lastVerifiedAt: '2026-08-30T21:15:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
    characterImage: settingsAssets.light.imgCharacterPortrait,
  },
  {
    contextId: 2,
    ownerUserId: 2,
    credentialId: 20,
    keySuffix: '77d0',
    status: 'ACTIVE',
    lastVerifiedAt: '2026-10-02T18:20:00',
    representativeCharacterName: '꼬마별궁수',
    characterClass: '보우마스터',
    worldName: '엘리시움',
    characterLevel: 260,
    characterImage: settingsAssets.light.imgCharacterPortrait3,
  },
]
let currentCredentialId = 10

export const dataContextHandlers = [
  http.get('*/api/v1/auth/data-contexts', () =>
    HttpResponse.json(
      contexts.map((item) => ({ ...item, current: item.credentialId === currentCredentialId }))
    )
  ),

  http.post('*/api/v1/auth/data-context', async ({ request }) => {
    const { credentialId } = (await request.json()) as { credentialId: number }
    if (!contexts.some((item) => item.credentialId === credentialId)) {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_ACCESS_DENIED', message: '데이터셋 접근 권한 없음' },
        { status: 401 }
      )
    }
    currentCredentialId = credentialId
    return HttpResponse.json({
      ...mockAuthToken,
      user: { id: 1, nickname: '미리보기', email: 'mock@example.com', isNewUser: false },
    })
  }),
]
