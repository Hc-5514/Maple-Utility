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
export type OAuthProvider = 'KAKAO' | 'NEXON_APIKEY'

export interface User {
  id: number
  oauthProvider: OAuthProvider
  email: string | null
  nickname: string | null
}

export interface AuthToken {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export interface AuthUserResponse {
  id: number
  nickname: string
  email: string | null
  isNewUser: boolean
}

export interface AuthLoginResponse extends AuthToken {
  user: AuthUserResponse
}

// ─── Nexon API 키 ─────────────────────────────────────────────────
export type ApiKeyStatus = 'ACTIVE' | 'INVALID' | 'EXPIRED'

export interface ApiKeyStatusResponse {
  registered: boolean
  keySuffix: string | null
  keyStatus: ApiKeyStatus | null
  lastVerifiedAt: string | null
}

// GET /auth/data-contexts 응답 행(키 단위). 대표 캐릭터는 첫 즐겨찾기, 없으면 최고 레벨 캐릭터
export interface DataContextItem {
  contextId: number
  ownerUserId: number
  credentialId: number
  keySuffix: string | null
  status: ApiKeyStatus
  lastVerifiedAt: string | null
  current: boolean
  representativeCharacterName: string | null
  characterClass: string | null
  worldName: string | null
  characterLevel: number | null
  characterImage: string | null
}

export interface ApiKeyRegistrationResponse extends ApiKeyStatusResponse {
  auth?: AuthLoginResponse | null
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
  favorite: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

// ─── 보스 ─────────────────────────────────────────────────────────
export type BossDifficulty = 'EASY' | 'NORMAL' | 'HARD' | 'CHAOS' | 'EXTREME'
export type ResetPeriod = 'WEEKLY' | 'MONTHLY'
export type DropRateTier = 'HIGH' | 'NORMAL' | 'LOW'
export type BossItemKind = 'CRYSTAL' | 'FIXED' | 'RANDOM'

export interface BossDropItem {
  id: number
  bossId: number
  itemName: string
  itemImage: string | null
  itemDescription: string | null
  dropRateTier: DropRateTier | null
  itemKind: BossItemKind
  defaultQuantity: number
}

export interface BossPeriodItem {
  dropItem: BossDropItem
  acquired: boolean
  quantity: number
  mesoAmount: number | null
}

export interface BossPeriod {
  bossId: number
  characterId: number
  periodStart: string
  partySize: number
  /** 보스·난이도별 입장 가능 최대 인원 (백엔드 반환) */
  maxPartySize: number
  crystalPrice: number
  savedAt: string | null
  items: BossPeriodItem[]
}

export interface BossPeriodDraft extends BossPeriod {
  dirty: boolean
}

export interface BossCandidate {
  id: number
  bossName: string
  difficulty: BossDifficulty
  bossImage: string | null
  crystalPrice: number
  resetPeriod: ResetPeriod
}

export interface BossItemAcquisition {
  id: number
  characterId: number
  bossDropItemId: number
  acquiredDate: string
  memo: string | null
}

export interface BossDropItemAcquisitionStatus {
  dropItem: BossDropItem
  acquired: boolean
  acquisitions: BossItemAcquisition[]
}

// ─── 스케줄러 (일간/주간/보스) ────────────────────────────────────
export interface SchedulerDailyRecord {
  id?: number
  characterId: number
  characterName?: string
  recordDate: string
  contentName: string
  completedCount: number
  totalCount: number
  syncedAt: string | null
}

export interface SchedulerWeeklyRecord {
  id?: number
  characterId: number
  characterName?: string
  weekStartDate: string
  contentName: string
  completed: boolean
  score: number | null
  syncedAt: string | null
}

export interface SchedulerBossRecord {
  id?: number
  characterId: number
  characterName?: string
  bossId?: number
  bossName?: string
  difficulty?: BossDifficulty
  bossImage?: string | null
  crystalPrice?: number
  /** 처치 시 획득한 결정 메소. 미획득이면 0, 미지정이면 crystalPrice 사용 */
  acquiredCrystalMeso?: number | null
  recordDate?: string
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
  huntingGroundId: number | null
  regionName: string | null
  huntingGround: string | null
  memo: string | null
  createdAt: string
  updatedAt: string
}

export interface HuntingSummary {
  recordCount: number
  totalMeso: number
  totalSolErda: number
  totalPlayDurationMin: number
}

export interface HuntingGround {
  id: number
  regionName: string
  mapName: string
  maxMonsterLevel: number
  favorite: boolean
}

// ─── 스케줄러 요약 (대시보드) ──────────────────────────────────────
export interface SchedulerProgress {
  completed: number
  total: number
}

export interface CharacterSchedulerSummary {
  hasRecords?: boolean
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
  syncedAt: string | null
}

export interface SchedulerBossDetail {
  weeklyBosses: SchedulerBossRecord[]
  monthlyBosses: SchedulerBossRecord[]
  weeklyCustomizable: boolean
  monthlyCustomizable: boolean
}

export interface SyncJob {
  id: number
  jobType: 'CHARACTER' | 'SCHEDULER'
  status: 'STARTED' | 'COMPLETED' | 'FAILED'
  totalCount: number
  completedCount: number
  skippedCount: number
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
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

// ─── 통계 ─────────────────────────────────────────────────────────
export interface StatsHuntingDaily {
  date: string
  mesoEarned: number
  solErdaEarned: number
  playDurationMin: number | null
}

export interface StatsHuntingSummary {
  totalMeso: number
  totalSolErda: number
  avgDailyMeso: number
  avgDailySolErda: number
  dailyRecords: StatsHuntingDaily[]
}

export interface StatsCrystalBossDetail {
  bossName: string
  difficulty: BossDifficulty
  income: number
}

export interface StatsCrystalWeekly {
  weekStart: string
  totalIncome: number
  bossDetails: StatsCrystalBossDetail[]
}

export interface StatsCrystalSummary {
  totalCrystalIncome: number
  weeklyAverage: number
  weeklyRecords: StatsCrystalWeekly[]
}

export interface StatsBossItem {
  acquiredDate: string
  characterName: string
  bossName: string
  difficulty: BossDifficulty
  itemName: string
}
