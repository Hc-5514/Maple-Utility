import snapshot from './nexonPreview.json'
import catalog from './nexonPreviewCatalog.json'
import type { BossDifficulty, BossItemKind, BossPeriod, Character, ResetPeriod, SchedulerBossRecord } from '../../types'

export const previewDate = snapshot.snapshotDate
export const previewWeekStart = '2026-10-01'
export const previewCharacters: Character[] = snapshot.characters.map((character, index) => ({
  id: character.id,
  userId: 1,
  ocid: `preview-character-${character.id}`,
  characterName: character.characterName,
  worldName: character.worldName,
  characterClass: character.characterClass,
  characterLevel: character.characterLevel,
  characterImage: character.characterImage,
  guildName: character.guildName,
  favorite: true,
  sortOrder: index,
  createdAt: `${previewDate}T00:00:00`,
  updatedAt: `${previewDate}T00:00:00`,
}))

export function previewCharacter(characterId: number) {
  return snapshot.characters.find((character) => character.id === characterId)
}

export function previewBoss(bossId: number) {
  return catalog.find((boss) => boss.id === bossId)
}

export function previewBossRecords(characterId: number): SchedulerBossRecord[] {
  const character = previewCharacter(characterId)
  if (!character) return []
  return character.bosses.flatMap((state) => {
    const boss = catalog.find((candidate) => candidate.bossName === state.bossName
      && candidate.difficulty === state.difficulty)
    if (!boss) return []
    return [{
      id: characterId * 10000 + boss.id,
      characterId,
      characterName: character.characterName,
      bossId: boss.id,
      bossName: boss.bossName,
      difficulty: boss.difficulty as BossDifficulty,
      bossImage: null,
      crystalPrice: boss.crystalPrice,
      recordDate: previewDate,
      resetPeriod: boss.resetPeriod as ResetPeriod,
      isCompleted: state.isCompleted,
      syncedAt: `${previewDate}T00:00:00`,
    }]
  })
}

const THREE_PARTY_BOSSES = ['최초의 대적자', '카링', '림보', '발드릭스', '유피테르', '벨로나']

/** 보스·난이도별 최대 인원: 기본 6인, 대적자·카링·림보·발드릭스·유피테르·벨로나 3인, 익스트림 스우 2인 */
export function previewMaxPartySize(bossName: string, difficulty: string) {
  if (bossName === '스우' && difficulty === 'EXTREME') return 2
  return THREE_PARTY_BOSSES.includes(bossName) ? 3 : 6
}

export function previewPartySize(characterId: number, bossId: number) {
  const boss = previewBoss(bossId)
  const max = boss ? previewMaxPartySize(boss.bossName, boss.difficulty) : 6
  return Math.min(max, 1 + ((characterId * 17 + bossId * 13) % 3))
}

const savedPeriods = new Map<string, BossPeriod>()

export function resetPreviewPeriods() {
  savedPeriods.clear()
}

function periodKey(characterId: number, bossId: number, periodStart: string) {
  return `${characterId}:${bossId}:${periodStart}`
}

export function getPreviewPeriod(characterId: number, bossId: number, periodStart: string): BossPeriod | null {
  const boss = previewBoss(bossId)
  if (!boss || !previewCharacter(characterId)) return null
  const key = periodKey(characterId, bossId, periodStart)
  const saved = savedPeriods.get(key)
  if (saved) return saved
  const partySize = previewPartySize(characterId, bossId)
  return {
    bossId,
    characterId,
    periodStart,
    partySize,
    maxPartySize: previewMaxPartySize(boss.bossName, boss.difficulty),
    crystalPrice: boss.crystalPrice,
    savedAt: null,
    items: boss.dropItems.map((dropItem) => ({
      dropItem: {
        ...dropItem,
        itemKind: dropItem.itemKind as BossItemKind,
        itemImage: null,
        itemDescription: null,
        dropRateTier: null,
      },
      acquired: dropItem.itemKind === 'CRYSTAL',
      quantity: dropItem.defaultQuantity,
      mesoAmount: dropItem.itemKind === 'CRYSTAL' ? Math.floor(boss.crystalPrice / partySize) : null,
    })),
  }
}

export function savePreviewPeriod(period: BossPeriod) {
  const saved = { ...period, savedAt: new Date().toISOString() }
  savedPeriods.set(periodKey(period.characterId, period.bossId, period.periodStart), saved)
  return saved
}

export function previewPeriods(characterId?: number | null) {
  const characters = characterId ? [characterId] : snapshot.characters.map((character) => character.id)
  return characters.flatMap((id) => previewBossRecords(id).map((record) =>
    getPreviewPeriod(id, record.bossId ?? 0, previewWeekStart)).filter((period): period is BossPeriod => period !== null))
}
