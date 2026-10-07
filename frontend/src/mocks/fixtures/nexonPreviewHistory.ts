import type { BossDifficulty, StatsBossItem } from '../../types'
import { previewBoss, previewCharacter, previewDate, previewPeriods } from './nexonPreview'

export const previewHistory: StatsBossItem[] = [1, 2].flatMap((characterId) => {
  const candidates = previewPeriods(characterId).flatMap((period) =>
    period.items.filter((item) => item.dropItem.itemKind !== 'CRYSTAL').map((item) => ({
      characterName: previewCharacter(characterId)?.characterName ?? '',
      bossName: previewBoss(period.bossId)?.bossName ?? '',
      difficulty: (previewBoss(period.bossId)?.difficulty ?? 'NORMAL') as BossDifficulty,
      itemName: item.dropItem.itemName,
    })))

  return Array.from({ length: 20 }, (_, index) => ({
    ...candidates[Math.floor(index * candidates.length / 20)],
    acquiredDate: `${previewDate.slice(0, 8)}${String(1 + index % 7).padStart(2, '0')}`,
  }))
})
