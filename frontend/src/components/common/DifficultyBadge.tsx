import type { BossDifficulty } from '../../types'

const COLOR: Record<BossDifficulty, string> = {
  EASY:    '#6b7280',
  NORMAL:  '#22c55e',
  HARD:    '#ef4444',
  CHAOS:   '#a855f7',
  EXTREME: '#f97316',
}

const LABEL: Record<BossDifficulty, string> = {
  EASY:    '이지',
  NORMAL:  '노말',
  HARD:    '하드',
  CHAOS:   '카오스',
  EXTREME: '익스트림',
}

interface Props {
  difficulty: BossDifficulty
}

export default function DifficultyBadge({ difficulty }: Props) {
  const color = COLOR[difficulty]
  return (
    <span
      className="inline-block rounded px-2 py-0.5 text-xs font-semibold"
      style={{ backgroundColor: `${color}20`, color }}
    >
      {LABEL[difficulty]}
    </span>
  )
}
