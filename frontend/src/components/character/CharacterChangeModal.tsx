import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import client from '../../api/client'
import CharacterPortrait from '../common/CharacterPortrait'
import ThemedDialog, { dialogCancel, dialogConfirm } from '../common/ThemedDialog'
import { worlds } from '../dashboard/worlds'
import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from './assets'
import type { ApiResponse, Character } from '../../types'

const avatarColors = [
  'border-[rgba(196,173,255,0.21)] bg-[rgba(196,173,255,0.09)]',
  'border-[rgba(126,220,195,0.21)] bg-[rgba(126,220,195,0.09)]',
  'border-[rgba(255,186,161,0.21)] bg-[rgba(255,186,161,0.09)]',
  'border-[rgba(246,203,125,0.21)] bg-[rgba(246,203,125,0.09)]',
  'border-[rgba(234,174,212,0.21)] bg-[rgba(234,174,212,0.09)]',
  'border-[rgba(145,196,245,0.21)] bg-[rgba(145,196,245,0.09)]',
]

interface Props {
  currentId: number
  onClose: () => void
  onChange: (id: number) => void
}

export default function CharacterChangeModal({ currentId, onClose, onChange }: Props) {
  const assets = characterAssets[useThemeStore().theme]
  const [selectedId, setSelectedId] = useState(currentId)
  const { data: characters = [], isLoading, isError } = useQuery({
    queryKey: ['characters', 'favorites'],
    queryFn: () => client.get<ApiResponse<Character[]>>('/characters/favorites').then((r) => r.data.data),
  })

  return (
    <ThemedDialog
      title="캐릭터 변경"
      description="즐겨찾기한 캐릭터 중에서 선택할 수 있습니다."
      width={880}
      top={220}
      onClose={onClose}
      actions={
        <>
          <button type="button" onClick={onClose} className={dialogCancel}>
            취소
          </button>
          <button
            type="button"
            onClick={() => (selectedId === currentId ? onClose() : onChange(selectedId))}
            className={dialogConfirm}
          >
            변경
          </button>
        </>
      }
    >
      {isLoading ? (
        <div className="grid h-[262px] grid-cols-1 content-start gap-3 sm:grid-cols-3">
          {[...Array(6)].map((_, i) => <div key={i} className="h-[94px] animate-pulse rounded-xl bg-[var(--dash-subtle)]" />)}
        </div>
      ) : isError ? (
        <p role="alert" className="text-[14px] text-[var(--dash-danger)]">즐겨찾기 캐릭터를 불러오지 못했습니다.</p>
      ) : (
        <div role="radiogroup" aria-label="즐겨찾기 캐릭터" className="dialog-scroll max-h-[262px] overflow-y-auto pr-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {characters.map((character, index) => {
              const selected = character.id === selectedId
              const world = worlds.find(([name]) => name === character.worldName)
              return (
                <button
                  key={character.id}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  aria-label={character.characterName}
                  onClick={() => setSelectedId(character.id)}
                  onDoubleClick={() => onChange(character.id)}
                  className={`flex items-center gap-3 rounded-xl px-4 py-[14px] text-left ${selected ? 'border-[1.5px] border-[var(--dash-accent)] bg-[var(--dash-selected)]' : 'border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)] hover:border-[var(--dash-border)]'}`}
                >
                  <span className={`flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-xl border ${selected ? avatarColors[0] : avatarColors[index % avatarColors.length]}`}>
                    {character.characterImage ? (
                      <CharacterPortrait src={character.characterImage} alt="" size={56} />
                    ) : (
                      <span className="text-3xl">🍁</span>
                    )}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col gap-[3px]">
                    <span className="flex items-center gap-[6px]">
                      <span className={`truncate text-[16px] font-bold ${selected ? 'text-[var(--dash-accent)]' : ''}`}>
                        {character.characterName}
                      </span>
                      {selected && (
                        <span className="ml-auto flex size-[18px] shrink-0 items-center justify-center rounded-full bg-[var(--dash-accent)]">
                          <img src={assets.imgCheckOnAccent} alt="" className="size-3" />
                        </span>
                      )}
                    </span>
                    <span className="truncate text-[12px]">
                      {[character.characterLevel !== null ? `Lv.${character.characterLevel}` : null, character.characterClass]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                    {character.worldName && (
                      <span className="flex items-center gap-1">
                        {world && <img src={world[1]} alt="" className="size-[14px] rounded-[2px] object-contain" />}
                        <span className="text-[11px] leading-[1.45] text-[var(--dash-muted)]">{character.worldName}</span>
                      </span>
                    )}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      )}
    </ThemedDialog>
  )
}
