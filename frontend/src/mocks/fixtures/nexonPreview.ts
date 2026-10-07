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

export function previewPartySize(characterId: number, bossId: number) {
  return 1 + ((characterId * 17 + bossId * 13) % 3)
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
  const completed = previewBossRecords(characterId).some((record) => record.bossId === bossId && record.isCompleted)
  const isSnapshotPeriod = periodStart === previewWeekStart
  return {
    bossId,
    characterId,
    periodStart,
    partySize,
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
      acquired: dropItem.itemKind === 'CRYSTAL' && completed && isSnapshotPeriod,
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
