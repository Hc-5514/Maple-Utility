// ─── 공통 API 응답 ───────────────────────────────────────────────
export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// ─── 인증 ─────────────────────────────────────────────────────────
export type OAuthProvider = 'KAKAO' | 'NEXON'

export interface User {
  id: number
  oauthProvider: OAuthProvider
  oauthId: string
  email: string | null
  nickname: string | null
  createdAt: string
  updatedAt: string
}

export interface AuthToken {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export interface AuthLoginResponse extends AuthToken {
  isNewUser: boolean
  user: User
}

// ─── Nexon API 키 ─────────────────────────────────────────────────
export type ApiKeyStatus = 'ACTIVE' | 'INVALID' | 'EXPIRED'

export interface UserApiKey {
  id: number
  userId: number
  keyStatus: ApiKeyStatus
  lastVerifiedAt: string | null
  createdAt: string
  updatedAt: string
}

// ─── 캐릭터 ──────────────────────────────────────────────────────
export interface Character {
  id: number
  userId: number
  ocid: string
  characterName: string
  worldName: string | null
  characterClass: string | null
  characterLevel: number | null
  characterImage: string | null
  guildName: string | null
  isFavorite: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

// ─── 보스 ─────────────────────────────────────────────────────────
export type BossDifficulty = 'EASY' | 'NORMAL' | 'HARD' | 'CHAOS' | 'EXTREME'
export type ResetPeriod = 'WEEKLY' | 'MONTHLY'
export type DropRateTier = 'HIGH' | 'NORMAL' | 'LOW'

export interface BossMaster {
  id: number
  bossName: string
  difficulty: BossDifficulty
  resetPeriod: ResetPeriod
  crystalPrice: number
  bossImage: string | null
  sortOrder: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface BossDropItem {
  id: number
  bossId: number
  itemName: string
  itemImage: string | null
  itemDescription: string | null
  dropRateTier: DropRateTier | null
}

export interface BossItemAcquisition {
  id: number
  characterId: number
  bossDropItemId: number
  acquiredDate: string
  memo: string | null
  createdAt: string
}

// ─── 스케줄러 (일간/주간/보스) ────────────────────────────────────
export interface SchedulerDailyRecord {
  id: number
  characterId: number
  recordDate: string
  contentName: string
  completedCount: number
  totalCount: number
  syncedAt: string | null
}

export interface SchedulerWeeklyRecord {
  id: number
  characterId: number
  weekStartDate: string
  contentName: string
  isCompleted: boolean
  score: number | null
  syncedAt: string | null
}

export interface SchedulerBossRecord {
  id: number
  characterId: number
  bossId: number
  recordDate: string
  resetPeriod: ResetPeriod
  isCompleted: boolean
  syncedAt: string | null
}

// ─── 사냥 기록 ────────────────────────────────────────────────────
export interface HuntingRecord {
  id: number
  characterId: number
  recordDate: string
  mesoEarned: number
  solErdaEarned: number
  playDurationMin: number | null
  huntingGround: string | null
  memo: string | null
  createdAt: string
  updatedAt: string
}

// ─── 스케줄러 요약 (대시보드) ──────────────────────────────────────
export interface SchedulerProgress {
  completed: number
  total: number
}

export interface CharacterSchedulerSummary {
  characterId: number
  characterName: string
  characterLevel: number | null
  characterClass: string | null
  characterImage: string | null
  worldName: string | null
  daily: SchedulerProgress
  weekly: SchedulerProgress
  weeklyBoss: SchedulerProgress
  monthlyBoss: SchedulerProgress
}

export interface SchedulerSummary {
  characters: CharacterSchedulerSummary[]
  syncedAt: string
}

// ─── 길드 콘텐츠 ─────────────────────────────────────────────────
export interface GuildRecord {
  id: number
  characterId: number
  recordDate: string
  contentName: string
  score: number | null
  syncedAt: string | null
}

// ─── 데이터 동기화 ────────────────────────────────────────────────
export type SyncType = 'SCHEDULER_BATCH' | 'SCHEDULER_REALTIME' | 'CHARACTER_SYNC'
export type SyncStatus = 'STARTED' | 'COMPLETED' | 'FAILED'

export interface DataSyncLog {
  id: number
  userId: number | null
  syncType: SyncType
  status: SyncStatus
  apiCallsUsed: number
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}
