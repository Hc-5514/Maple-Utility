import { useNavigate, useParams } from 'react-router-dom'
import CharacterSelector from '../components/common/CharacterSelector'
import GuildContent from '../components/character/GuildContent'
import DailyContent from '../components/character/DailyContent'
import WeeklyContent from '../components/character/WeeklyContent'
import BossContent from '../components/character/BossContent'
import { useCharacter, useToggleFavorite } from '../hooks/useCharacterDetail'

function getWeekStart(date: Date): string {
  const d = new Date(date)
  d.setDate(d.getDate() - d.getDay())
  return d.toISOString().split('T')[0]
}

export default function CharacterDetailPage() {
  const { characterId } = useParams<{ characterId: string }>()
  const navigate = useNavigate()
  const id = Number(characterId)

  const today = new Date().toISOString().split('T')[0]
  const weekStart = getWeekStart(new Date())

  const { data: character, isLoading: charLoading } = useCharacter(id)
  const toggleFav = useToggleFavorite(id)

  if (!id || Number.isNaN(id)) {
    return <p className="text-[#f87171]">잘못된 캐릭터 ID입니다.</p>
  }

  return (
    <div className="space-y-5">
      {/* 캐릭터 헤더 */}
      <div className="flex flex-wrap items-center gap-4 rounded-xl bg-[#2d2d44] p-5">
        {charLoading ? (
          <div className="h-20 w-20 animate-pulse rounded-lg bg-white/10" />
        ) : character?.characterImage ? (
          <img
            src={character.characterImage}
            alt={character.characterName}
            className="h-20 w-20 rounded-lg object-contain"
          />
        ) : (
          <div className="flex h-20 w-20 items-center justify-center rounded-lg bg-[#1a1a2e] text-4xl">
            🍁
          </div>
        )}

        <div className="flex-1 min-w-0">
          {charLoading ? (
            <div className="space-y-2">
              <div className="h-6 w-40 animate-pulse rounded bg-white/10" />
              <div className="h-4 w-56 animate-pulse rounded bg-white/10" />
            </div>
          ) : character ? (
            <>
              <p className="text-xl font-bold text-white">{character.characterName}</p>
              <p className="mt-0.5 text-sm text-white/50">
                {[
                  character.characterLevel !== null ? `Lv.${character.characterLevel}` : null,
                  character.characterClass,
                  character.worldName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </>
          ) : null}
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => toggleFav.mutate()}
            disabled={toggleFav.isPending || charLoading}
            className="text-2xl transition-opacity hover:opacity-70 disabled:opacity-40"
            aria-label={character?.favorite ? '즐겨찾기 해제' : '즐겨찾기 추가'}
          >
            {character?.favorite ? '★' : '☆'}
          </button>
          <CharacterSelector
            selectedId={id}
            onChange={(newId) => navigate(`/dashboard/${newId}`)}
          />
        </div>
      </div>

      {/* 콘텐츠 섹션 */}
      <GuildContent characterId={id} date={today} />
      <DailyContent characterId={id} date={today} />
      <WeeklyContent characterId={id} weekStart={weekStart} />
      <BossContent characterId={id} date={today} />
    </div>
  )
}
