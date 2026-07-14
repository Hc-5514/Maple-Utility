import { useNavigate } from 'react-router-dom'
import ProgressBar from '../common/ProgressBar'
import type { CharacterSchedulerSummary } from '../../types'

interface Props {
  character: CharacterSchedulerSummary
}

const progressItems = [
  { key: 'daily', label: '일일' },
  { key: 'weekly', label: '주간' },
  { key: 'weeklyBoss', label: '주보스' },
  { key: 'monthlyBoss', label: '월보스' },
] as const

export default function CharacterSummaryCard({ character }: Props) {
  const navigate = useNavigate()

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => navigate(`/dashboard/${character.characterId}`)}
      onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/dashboard/${character.characterId}`) }}
      className="cursor-pointer rounded-xl bg-[#2d2d44] p-5 transition-colors hover:bg-[#3a3a5c] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#4ade80]"
    >
      <div className="mb-4 flex items-center gap-3">
        {character.characterImage ? (
          <img
            src={character.characterImage}
            alt={character.characterName}
            className="h-16 w-16 rounded-lg object-contain"
          />
        ) : (
          <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-[#1a1a2e] text-3xl">
            🍁
          </div>
        )}
        <div className="min-w-0">
          <p className="truncate font-bold text-white">{character.characterName}</p>
          <p className="truncate text-sm text-white/50">
            {[
              character.characterLevel !== null ? `Lv.${character.characterLevel}` : null,
              character.characterClass,
              character.worldName,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </div>
      </div>

      <div className="space-y-2">
        {progressItems.map(({ key, label }) => {
          const { completed, total } = character[key]
          return (
            <div key={key} className="flex items-center gap-2">
              <span className="w-10 shrink-0 text-right text-xs text-white/50">{label}</span>
              <ProgressBar value={completed} max={total || 1} className="flex-1" />
              <span className="w-8 shrink-0 text-right text-xs text-white/50">
                {completed}/{total}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
