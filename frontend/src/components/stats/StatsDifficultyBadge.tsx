import type { BossDifficulty } from '../../types'

const labels: Record<BossDifficulty, string> = {
  EASY: '이지',
  NORMAL: '노멀',
  HARD: '하드',
  CHAOS: '카오스',
  EXTREME: '익스트림',
}

// Figma: 익스트림은 accent, 하드·카오스는 danger 색. 이지·노멀은 Figma 미정으로 기존 난이도 색 계열 사용
const tones: Record<BossDifficulty, string> = {
  EXTREME: 'border-[var(--dash-accent)]/16 bg-[var(--dash-accent)]/8 text-[var(--dash-accent)]',
  CHAOS: 'border-[var(--dash-danger)]/16 bg-[var(--dash-danger)]/8 text-[var(--dash-danger)]',
  HARD: 'border-[var(--dash-danger)]/16 bg-[var(--dash-danger)]/8 text-[var(--dash-danger)]',
  NORMAL: 'border-[var(--dash-normal)]/16 bg-[var(--dash-normal)]/8 text-[var(--dash-normal)]',
  EASY: 'border-[var(--dash-easy)]/16 bg-[var(--dash-easy)]/8 text-[var(--dash-easy)]',
}

export default function StatsDifficultyBadge({ difficulty }: { difficulty: BossDifficulty }) {
  return (
    <span className={`rounded-full border px-[10px] py-1 text-[11px] font-bold whitespace-nowrap ${tones[difficulty]}`}>
      {labels[difficulty]}
    </span>
  )
}
