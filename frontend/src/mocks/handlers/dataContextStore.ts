import type { ApiKeyStatus, DataContextItem } from '../../types'
import { settingsAssets } from '../../components/settings/assets'

// 연결된 넥슨 계정(데이터셋) 목 상태. apiKey/dataContext/auth 핸들러가 함께 사용한다.
export const MAX_MOCK_ACCOUNTS = 5

interface MockKey {
  credentialId: number
  keySuffix: string
  status: ApiKeyStatus
  lastVerifiedAt: string
}

interface MockAccount {
  contextId: number
  ownerUserId: number
  name: string
  characterClass: string
  worldName: string
  level: number
  image: string
  linkedAt: string
  keys: MockKey[]
}

const portraits = [
  settingsAssets.light.imgCharacterPortrait,
  settingsAssets.light.imgCharacterPortrait1,
  settingsAssets.light.imgCharacterPortrait3,
  settingsAssets.light.imgCharacterPortrait4,
  settingsAssets.light.imgCharacterPortrait5,
]

const templates = [
  { name: '꼬농', characterClass: '아크메이지(썬,콜)', level: 294 },
  { name: '말랑꼬농', characterClass: '윈드브레이커', level: 285 },
  { name: '꼬마별궁수', characterClass: '보우마스터', level: 260 },
  { name: '도구꼬농', characterClass: '비숍', level: 251 },
  { name: '도끼꼬농', characterClass: '히어로', level: 250 },
]

function account(index: number, keys: Array<[string, ApiKeyStatus, string]>): MockAccount {
  const template = templates[index]
  return {
    contextId: index + 1,
    ownerUserId: index + 1,
    name: template.name,
    characterClass: template.characterClass,
    worldName: '엘리시움',
    level: template.level,
    image: portraits[index],
    linkedAt: `2026-09-${String(10 + index).padStart(2, '0')}T09:00:00`,
    keys: keys.map(([keySuffix, status, lastVerifiedAt], keyIndex) => ({
      credentialId: (index + 1) * 10 + keyIndex,
      keySuffix,
      status,
      lastVerifiedAt,
    })),
  }
}

export type MockAccountScenario = 'default' | 'multi' | 'single' | 'limit' | 'apikey'

const scenarioInput = import.meta.env.VITE_MOCK_SCENARIO as string | undefined
export const mockAccountScenario: MockAccountScenario =
  scenarioInput === 'settings-multi-account'
    ? 'multi'
    : scenarioInput === 'settings-single-account'
      ? 'single'
      : scenarioInput === 'settings-account-limit'
        ? 'limit'
        : scenarioInput === 'settings-apikey-login'
          ? 'apikey'
          : 'default'

// 목 실패 주입: VITE_MOCK_FAIL=precheck|add|unlink|delete
export const mockFailure = import.meta.env.VITE_MOCK_FAIL as string | undefined

function seed(scenario: MockAccountScenario): MockAccount[] {
  switch (scenario) {
    case 'multi':
      return [
        account(0, [
          ['9f2a', 'ACTIVE', '2026-09-17T09:42:00'],
          ['4c7e', 'ACTIVE', '2026-09-16T18:30:00'],
          ['1a08', 'EXPIRED', '2026-08-02T21:15:00'],
        ]),
        account(1, [
          ['3b1c', 'ACTIVE', '2026-09-17T09:42:00'],
          ['d5e2', 'ACTIVE', '2026-09-15T11:08:00'],
        ]),
        account(2, [['77d0', 'ACTIVE', '2026-09-17T09:42:00']]),
      ]
    case 'single':
    case 'apikey':
      return [account(0, [['9f2a', 'ACTIVE', '2026-09-17T09:42:00']])]
    case 'limit':
      return [
        account(0, [
          ['9f2a', 'ACTIVE', '2026-09-17T09:42:00'],
          ['4c7e', 'ACTIVE', '2026-09-16T18:30:00'],
          ['1a08', 'EXPIRED', '2026-08-02T21:15:00'],
        ]),
        account(1, [
          ['3b1c', 'ACTIVE', '2026-09-17T09:42:00'],
          ['d5e2', 'ACTIVE', '2026-09-15T11:08:00'],
        ]),
        account(2, [['77d0', 'ACTIVE', '2026-09-17T09:42:00']]),
        account(3, [['e8a1', 'ACTIVE', '2026-09-14T20:12:00']]),
        account(4, [['b402', 'ACTIVE', '2026-09-13T08:47:00']]),
      ]
    default: {
      // 기존 기본 시나리오: 키 2개(ACTIVE·EXPIRED)인 계정과 키 1개인 계정
      const first = account(0, [
        ['9f2a', 'ACTIVE', '2026-09-17T09:42:00'],
        ['1b3c', 'EXPIRED', '2026-08-30T21:15:00'],
      ])
      first.keys[0].credentialId = 10
      first.keys[1].credentialId = 11
      const second = account(2, [['77d0', 'ACTIVE', '2026-10-02T18:20:00']])
      second.contextId = 2
      second.ownerUserId = 2
      second.keys[0].credentialId = 20
      return [first, second]
    }
  }
}

let accounts: MockAccount[] = seed(mockAccountScenario)
let currentCredentialId: number = accounts[0].keys[0].credentialId
let nextContextId = 100
let nextCredentialId = 1000

export function resetDataContextStore(scenario: MockAccountScenario = mockAccountScenario) {
  accounts = seed(scenario)
  currentCredentialId = accounts[0].keys[0].credentialId
  nextContextId = 100
  nextCredentialId = 1000
}

export function accountCount() {
  return accounts.length
}

export function currentContextId() {
  return accounts.find((item) => item.keys.some((key) => key.credentialId === currentCredentialId))?.contextId ?? null
}

// 현재 사용 중 계정 우선, 이후 연결(등록)순. 계정 내 키는 credentialId 순
export function listItems(): DataContextItem[] {
  const current = currentContextId()
  const ordered = [...accounts].sort((a, b) => {
    if (a.contextId === current) return -1
    if (b.contextId === current) return 1
    return a.linkedAt.localeCompare(b.linkedAt) || a.contextId - b.contextId
  })
  return ordered.flatMap((item) =>
    [...item.keys]
      .sort((a, b) => a.credentialId - b.credentialId)
      .map((key) => ({
        contextId: item.contextId,
        ownerUserId: item.ownerUserId,
        credentialId: key.credentialId,
        keySuffix: key.keySuffix,
        status: key.status,
        lastVerifiedAt: key.lastVerifiedAt,
        linkedAt: item.linkedAt,
        current: key.credentialId === currentCredentialId,
        representativeCharacterName: item.name,
        characterClass: item.characterClass,
        worldName: item.worldName,
        characterLevel: item.level,
        characterImage: item.image,
      }))
  )
}

export function selectCredential(credentialId: number): boolean {
  if (!accounts.some((item) => item.keys.some((key) => key.credentialId === credentialId))) return false
  currentCredentialId = credentialId
  return true
}

// 키 문자열 규칙으로 사전 확인/추가 결과를 재현한다.
export type MockPrecheckStatus = 'AVAILABLE' | 'ALREADY_LINKED' | 'LINKED_TO_OTHER_KAKAO' | 'LIMIT_EXCEEDED'

export function precheck(apiKey: string): MockPrecheckStatus {
  const key = apiKey.trim()
  if (key.includes('other-kakao')) return 'LINKED_TO_OTHER_KAKAO'
  if (accounts.some((item) => item.keys.some((entry) => key.endsWith(entry.keySuffix)))) return 'ALREADY_LINKED'
  if (accounts.length >= MAX_MOCK_ACCOUNTS) return 'LIMIT_EXCEEDED'
  return 'AVAILABLE'
}

export function addAccount(apiKey: string): MockAccount {
  const index = accounts.length % templates.length
  const template = templates[index]
  const contextId = nextContextId++
  const created: MockAccount = {
    contextId,
    ownerUserId: contextId,
    name: `${template.name}${contextId}`,
    characterClass: template.characterClass,
    worldName: '엘리시움',
    level: template.level,
    image: portraits[index],
    linkedAt: new Date().toISOString().slice(0, 19),
    keys: [
      {
        credentialId: nextCredentialId++,
        keySuffix: apiKey.trim().slice(-4),
        status: 'ACTIVE',
        lastVerifiedAt: new Date().toISOString().slice(0, 19),
      },
    ],
  }
  accounts = [...accounts, created]
  currentCredentialId = created.keys[0].credentialId
  return created
}

// 계정 제거. 현재 계정이면 남은 첫 계정으로 전환하고, 남은 계정이 없으면 null
export function removeAccount(contextId: number): { removed: boolean; wasCurrent: boolean } {
  const target = accounts.find((item) => item.contextId === contextId)
  if (!target) return { removed: false, wasCurrent: false }
  const wasCurrent = target.keys.some((key) => key.credentialId === currentCredentialId)
  accounts = accounts.filter((item) => item.contextId !== contextId)
  if (wasCurrent) currentCredentialId = listFirstCredentialId() ?? -1
  return { removed: true, wasCurrent }
}

function listFirstCredentialId(): number | null {
  const first = [...accounts].sort((a, b) => a.linkedAt.localeCompare(b.linkedAt) || a.contextId - b.contextId)[0]
  return first ? first.keys[0].credentialId : null
}
