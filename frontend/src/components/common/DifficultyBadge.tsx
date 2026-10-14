import type { BossDifficulty } from '../../types'

export const DIFFICULTY_COLOR: Record<BossDifficulty, string> = {
  EASY: 'var(--dash-easy)',
  NORMAL: 'var(--dash-normal)',
  HARD: 'var(--dash-hard)',
  CHAOS: 'var(--dash-chaos)',
  EXTREME: 'var(--dash-extreme)',
}

interface Props {
  difficulty: BossDifficulty
}

export default function DifficultyBadge({ difficulty }: Props) {
  const color = DIFFICULTY_COLOR[difficulty]
  return (
    <span
      className="inline-flex items-center rounded-[6px] px-2 py-[2px] text-[11px] leading-[13px] font-semibold [font-family:Inter,sans-serif]"
      style={{ backgroundColor: `color-mix(in srgb, ${color} 14%, transparent)`, color }}
    >
      {difficulty}
    </span>
  )
}
