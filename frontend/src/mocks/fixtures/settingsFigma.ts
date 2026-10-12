import type { Character } from '../../types'
import { settingsAssets } from '../../components/settings/assets'
import { worldNames } from '../../components/dashboard/worlds'
export const settingsFigmaScenario = import.meta.env.VITE_MOCK_SCENARIO === 'settings-figma'
const names = ['꼬농', '말랑꼬농', '꼬마렌의일기', '꼬마별궁수', '도구꼬농', '도끼꼬농']
const classes = ['아크메이지(썬,콜)', '윈드브레이커', '렌', '보우마스터', '비숍', '히어로']
const levels = [294, 285, 260, 260, 251, 250]
const assets = settingsAssets.light
const portraits = [
  assets.imgCharacterPortrait,
  assets.imgCharacterPortrait1,
  assets.imgCharacterPortrait2,
  assets.imgCharacterPortrait3,
  assets.imgCharacterPortrait4,
  assets.imgCharacterPortrait5,
]
export const settingsFigmaCharacters: Character[] = names.map((characterName, index) => ({
  id: index + 1,
  userId: 1,
  ocid: `settings-preview-${index + 1}`,
  characterName,
  characterClass: classes[index],
  characterLevel: levels[index],
  characterImage: portraits[index],
  worldName: '엘리시움',
  guildName: null,
  favorite: index < 2,
  sortOrder: index === 0 ? 1 : index === 1 ? 0 : index,
  createdAt: '2026-09-17T09:42:00',
  updatedAt: '2026-09-17T09:42:00',
}))
export const settingsFigmaWorlds = worldNames.filter(
  (world) => !['이노시스', '에오스'].includes(world)
)
