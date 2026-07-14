import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import Modal from '../components/common/Modal'
import type { ApiResponse, ApiKeyStatus, Character, UserApiKey } from '../types'

// ─── API Key 섹션 ──────────────────────────────────────────────────────────────

const statusConfig: Record<ApiKeyStatus, { label: string; color: string }> = {
  ACTIVE: { label: 'ACTIVE', color: '#4ade80' },
  INVALID: { label: 'INVALID', color: '#f97316' },
  EXPIRED: { label: 'EXPIRED', color: '#f87171' },
}

function ApiKeySection() {
  const queryClient = useQueryClient()
  const { setHasApiKey } = useAuthStore()
  const [inputKey, setInputKey] = useState('')
  const [showDeleteModal, setShowDeleteModal] = useState(false)

  const { data: apiKeyRes, isLoading } = useQuery({
    queryKey: ['user-api-keys'],
    queryFn: async () => {
      try {
        const { data } = await client.get<ApiResponse<UserApiKey>>('/user-api-keys')
        return data.data
      } catch {
        return null
      }
    },
  })

  const registerMutation = useMutation({
    mutationFn: async (apiKey: string) => {
      const { data } = await client.post<ApiResponse<UserApiKey>>('/user-api-keys', { apiKey })
      return data.data
    },
    onSuccess: () => {
      setHasApiKey(true)
      setInputKey('')
      void queryClient.invalidateQueries({ queryKey: ['user-api-keys'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await client.delete(`/user-api-keys/${id}`)
    },
    onSuccess: () => {
      setHasApiKey(false)
      setShowDeleteModal(false)
      void queryClient.invalidateQueries({ queryKey: ['user-api-keys'] })
    },
  })

  const hasKey = apiKeyRes !== null && apiKeyRes !== undefined
  const isActive = hasKey && apiKeyRes.keyStatus === 'ACTIVE'

  return (
    <section className="rounded-xl bg-[#2d2d44] p-6">
      <h2 className="mb-4 text-lg font-semibold text-white">Nexon API Key</h2>

      {isLoading ? (
        <div className="flex items-center gap-2 text-white/50">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white/80" />
          불러오는 중...
        </div>
      ) : hasKey ? (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <span
              className="rounded-full px-3 py-1 text-xs font-semibold"
              style={{
                backgroundColor: `${statusConfig[apiKeyRes.keyStatus].color}22`,
                color: statusConfig[apiKeyRes.keyStatus].color,
                border: `1px solid ${statusConfig[apiKeyRes.keyStatus].color}44`,
              }}
            >
              {statusConfig[apiKeyRes.keyStatus].label}
            </span>
            <span className="text-sm text-white/50">
              {apiKeyRes.lastVerifiedAt
                ? `마지막 인증: ${new Date(apiKeyRes.lastVerifiedAt).toLocaleDateString('ko-KR')}`
                : '미인증'}
            </span>
          </div>

          {!isActive && (
            <p className="text-sm text-[#f87171]">
              API Key가 유효하지 않습니다. 새로운 키를 등록해 주세요.
            </p>
          )}

          <div className="flex gap-2">
            {!isActive && (
              <div className="flex flex-1 gap-2">
                <input
                  type="text"
                  value={inputKey}
                  onChange={(e) => setInputKey(e.target.value)}
                  placeholder="새 Nexon API Key 입력"
                  className="flex-1 rounded-lg border border-white/10 bg-[#1a1a2e] px-3 py-2 text-sm text-white placeholder-white/30 focus:border-[#4ade80]/50 focus:outline-none"
                />
                <button
                  onClick={() => registerMutation.mutate(inputKey)}
                  disabled={!inputKey.trim() || registerMutation.isPending}
                  className="rounded-lg bg-[#4ade80] px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-40"
                >
                  {registerMutation.isPending ? '등록 중...' : '등록'}
                </button>
              </div>
            )}
            <button
              onClick={() => setShowDeleteModal(true)}
              className="rounded-lg border border-[#f87171]/40 px-4 py-2 text-sm font-semibold text-[#f87171] transition-colors hover:bg-[#f87171]/10"
            >
              삭제
            </button>
          </div>

          {registerMutation.isError && (
            <p className="text-sm text-[#f87171]">등록 중 오류가 발생했습니다.</p>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-white/50">
            넥슨 게임 데이터를 불러오려면 API Key를 등록해 주세요.
          </p>
          <div className="flex gap-2">
            <input
              type="text"
              value={inputKey}
              onChange={(e) => setInputKey(e.target.value)}
              placeholder="Nexon Open API Key 입력"
              className="flex-1 rounded-lg border border-white/10 bg-[#1a1a2e] px-3 py-2 text-sm text-white placeholder-white/30 focus:border-[#4ade80]/50 focus:outline-none"
            />
            <button
              onClick={() => registerMutation.mutate(inputKey)}
              disabled={!inputKey.trim() || registerMutation.isPending}
              className="rounded-lg bg-[#4ade80] px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-40"
            >
              {registerMutation.isPending ? '등록 중...' : '등록'}
            </button>
          </div>
          {registerMutation.isError && (
            <p className="text-sm text-[#f87171]">등록 중 오류가 발생했습니다.</p>
          )}
        </div>
      )}

      <Modal
        isOpen={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title="API Key 삭제"
      >
        <p className="mb-6 text-sm text-white/70">
          API Key를 삭제하면 게임 데이터 조회가 불가능해집니다. 정말 삭제하시겠습니까?
        </p>
        <div className="flex justify-end gap-3">
          <button
            onClick={() => setShowDeleteModal(false)}
            className="rounded-lg px-4 py-2 text-sm text-white/60 transition-colors hover:text-white"
          >
            취소
          </button>
          <button
            onClick={() => { if (apiKeyRes) deleteMutation.mutate(apiKeyRes.id) }}
            disabled={deleteMutation.isPending}
            className="rounded-lg bg-[#f87171] px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {deleteMutation.isPending ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </Modal>
    </section>
  )
}

// ─── 캐릭터 행 ─────────────────────────────────────────────────────────────────

function CharacterRow({ character }: { character: Character }) {
  const queryClient = useQueryClient()
  const { updateFavorite } = useCharacterStore()

  const favoriteMutation = useMutation({
    mutationFn: async () => {
      const { data } = await client.put<ApiResponse<Character>>(
        `/characters/${character.id}/favorite`,
      )
      return data.data
    },
    onSuccess: (updated) => {
      updateFavorite(character.id, updated.isFavorite)
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
    },
  })

  return (
    <div className="flex items-center gap-4 rounded-lg bg-[#1a1a2e] px-4 py-3">
      {character.characterImage ? (
        <img
          src={character.characterImage}
          alt={character.characterName}
          className="h-16 w-16 rounded-lg object-contain"
        />
      ) : (
        <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-[#3a3a5c] text-2xl">
          🍁
        </div>
      )}

      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-white">{character.characterName}</p>
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

      <button
        onClick={() => favoriteMutation.mutate()}
        disabled={favoriteMutation.isPending}
        className="text-xl transition-opacity hover:opacity-70 disabled:opacity-40"
        aria-label={character.isFavorite ? '즐겨찾기 해제' : '즐겨찾기 추가'}
      >
        {character.isFavorite ? '★' : '☆'}
      </button>
    </div>
  )
}

// ─── 캐릭터 관리 섹션 ──────────────────────────────────────────────────────────

function CharacterSection() {
  const queryClient = useQueryClient()
  const { characters, setCharacters } = useCharacterStore()

  const { isLoading } = useQuery({
    queryKey: ['characters'],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<Character[]>>('/characters')
      setCharacters(data.data)
      return data.data
    },
  })

  const syncMutation = useMutation({
    mutationFn: async () => {
      const { data } = await client.post<ApiResponse<Character[]>>('/characters/sync')
      return data.data
    },
    onSuccess: (synced) => {
      setCharacters(synced)
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
    },
  })

  const sorted = [...characters].sort((a, b) => {
    if (a.isFavorite === b.isFavorite) return a.sortOrder - b.sortOrder
    return a.isFavorite ? -1 : 1
  })

  return (
    <section className="rounded-xl bg-[#2d2d44] p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-white">
          캐릭터 관리
          {characters.length > 0 && (
            <span className="ml-2 text-sm font-normal text-white/40">{characters.length}명</span>
          )}
        </h2>
        <button
          onClick={() => syncMutation.mutate()}
          disabled={syncMutation.isPending}
          className="flex items-center gap-2 rounded-lg border border-white/10 px-3 py-1.5 text-sm text-white/70 transition-colors hover:border-white/20 hover:text-white disabled:opacity-40"
        >
          {syncMutation.isPending ? (
            <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white/80" />
          ) : (
            '↺'
          )}
          재동기화
        </button>
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 text-white/50">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white/80" />
          불러오는 중...
        </div>
      ) : sorted.length === 0 ? (
        <p className="text-sm text-white/40">
          등록된 캐릭터가 없습니다. 재동기화 버튼을 눌러 캐릭터를 불러오세요.
        </p>
      ) : (
        <div className="space-y-2">
          {sorted.map((char) => (
            <CharacterRow key={char.id} character={char} />
          ))}
        </div>
      )}

      {syncMutation.isError && (
        <p className="mt-3 text-sm text-[#f87171]">동기화 중 오류가 발생했습니다.</p>
      )}
    </section>
  )
}

// ─── 페이지 ────────────────────────────────────────────────────────────────────

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">설정</h1>
      <ApiKeySection />
      <CharacterSection />
    </div>
  )
}
