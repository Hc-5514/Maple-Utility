import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import DailyContent from '../components/character/DailyContent'
import WeeklyContent from '../components/character/WeeklyContent'
import BossContent from '../components/character/BossContent'
import CharacterPortrait from '../components/common/CharacterPortrait'
import CharacterChangeModal from '../components/character/CharacterChangeModal'
import { characterAssets } from '../components/character/assets'
import { detailCard } from '../components/character/styles'
import { worlds } from '../components/dashboard/worlds'
import { useCharacter, useToggleFavorite } from '../hooks/useCharacterDetail'
import { useThemeStore } from '../stores/themeStore'
import { localDate, thursdayWeekStart } from '../utils/date'

export default function CharacterDetailPage() {
  const { characterId } = useParams<{ characterId: string }>()
  const navigate = useNavigate()
  const id = Number(characterId)
  const assets = characterAssets[useThemeStore().theme]
  const [changing, setChanging] = useState(false)

  const today = localDate(new Date())
  const weekStart = thursdayWeekStart(new Date())

  const { data: character, isLoading: charLoading, isError: charError } = useCharacter(id)
  const toggleFav = useToggleFavorite(id)
  const world = worlds.find(([name]) => name === character?.worldName)

  if (!id || Number.isNaN(id)) {
    return <p className="text-[var(--dash-danger)]">잘못된 캐릭터 ID입니다.</p>
  }

  if (charError) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-[var(--dash-danger)]">캐릭터 정보를 불러오지 못했습니다.</p>
        <button type="button" onClick={() => navigate(-1)} className="settings-button">
          뒤로가기
        </button>
      </div>
    )
  }

  if (!charLoading && !character) {
    return <p className="text-[var(--dash-danger)]">즐겨찾기 캐릭터를 찾을 수 없습니다.</p>
  }

  return (
    <div className="flex flex-col gap-6 leading-[1.2]">
      <section className={`${detailCard} flex items-center gap-5 px-7 py-6`}>
        <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-[rgba(196,173,255,0.21)] bg-[rgba(196,173,255,0.09)]">
          {charLoading ? (
            <div className="size-[72px] animate-pulse rounded-xl bg-[var(--dash-track)]" />
          ) : character?.characterImage ? (
            <CharacterPortrait src={character.characterImage} alt={character.characterName} size={72} />
          ) : (
            <span className="text-4xl">🍁</span>
          )}
        </div>

        <div className="flex min-w-0 flex-col gap-[6px]">
          {charLoading ? (
            <>
              <div className="h-7 w-40 animate-pulse rounded bg-[var(--dash-track)]" />
              <div className="h-4 w-56 animate-pulse rounded bg-[var(--dash-track)]" />
            </>
          ) : character ? (
            <>
              <div className="flex items-center gap-2">
                <h1 className="truncate text-[26px] font-bold">{character.characterName}</h1>
                <button
                  type="button"
                  onClick={() => toggleFav.mutate(undefined, { onSuccess: () => navigate('/dashboard') })}
                  disabled={toggleFav.isPending}
                  aria-label={character.favorite ? '즐겨찾기 해제' : '즐겨찾기 추가'}
                  className="shrink-0 transition-opacity hover:opacity-70 disabled:opacity-40"
                >
                  <img src={assets.imgStar} alt="" className="size-5" />
                </button>
              </div>
              <p className="text-[14px] text-[var(--dash-muted)]">
                {[
                  character.characterLevel !== null ? `Lv.${character.characterLevel}` : null,
                  character.characterClass,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
              {character.worldName && (
                <div className="flex items-center gap-1">
                  {world && <img src={world[1]} alt="" className="size-4 rounded-[2px] object-contain" />}
                  <span className="text-[13px] leading-[1.45] text-[var(--dash-muted)]">{character.worldName}</span>
                </div>
              )}
            </>
          ) : null}
        </div>

        <button
          type="button"
          disabled={charLoading}
          onClick={() => setChanging(true)}
          className="ml-auto flex h-11 shrink-0 items-center gap-2 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-4 text-[14px] font-bold"
        >
          <img src={assets.imgSwap} alt="" className="size-4" />
          캐릭터 변경
        </button>
      </section>

      <DailyContent characterId={id} date={today} />
      <WeeklyContent characterId={id} weekStart={weekStart} />
      <BossContent key={id} characterId={id} date={today} />
      {changing && (
        <CharacterChangeModal
          currentId={id}
          onClose={() => setChanging(false)}
          onChange={(nextId) => {
            setChanging(false)
            navigate(`/dashboard/${nextId}`)
          }}
        />
      )}
    </div>
  )
}
