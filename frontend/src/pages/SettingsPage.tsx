import { useEffect, useState } from 'react'
import { ArrowDown, ArrowUp, RefreshCw, Save } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import Modal from '../components/common/Modal'
import type { ApiKeyStatus, ApiKeyStatusResponse, ApiResponse, Character, SyncJob } from '../types'

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
    queryKey: ['api-key/status'],
    queryFn: async () => {
      try {
        const { data } = await client.get<ApiResponse<ApiKeyStatusResponse>>('/api-key/status')
        return data.data
      } catch {
        return null
      }
    },
  })

  const registerMutation = useMutation({
    mutationFn: async (apiKey: string) => {
      await client.post('/api-key', { apiKey })
    },
    onSuccess: () => {
      setHasApiKey(true)
      setInputKey('')
      void queryClient.invalidateQueries({ queryKey: ['api-key/status'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      await client.delete('/api-key')
    },
    onSuccess: () => {
      setHasApiKey(false)
      setShowDeleteModal(false)
      void queryClient.invalidateQueries({ queryKey: ['api-key/status'] })
    },
  })

  const hasKey = apiKeyRes?.registered === true
  const keyStatus = apiKeyRes?.keyStatus ?? null
  const isActive = hasKey && keyStatus === 'ACTIVE'

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
                backgroundColor: `${statusConfig[keyStatus ?? 'INVALID'].color}22`,
                color: statusConfig[keyStatus ?? 'INVALID'].color,
                border: `1px solid ${statusConfig[keyStatus ?? 'INVALID'].color}44`,
              }}
            >
              {statusConfig[keyStatus ?? 'INVALID'].label}
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
				className="ml-auto rounded-lg border border-[#f87171]/40 px-4 py-2 text-sm font-semibold text-[#f87171] transition-colors hover:bg-[#f87171]/10"
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
            onClick={() => deleteMutation.mutate()}
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

function CharacterRow({ character, disabled, reorderDisabled, onMoveUp, onMoveDown }: {
  character: Character
  disabled: boolean
  reorderDisabled: boolean
  onMoveUp?: () => void
  onMoveDown?: () => void
}) {
  const queryClient = useQueryClient()
  const { updateFavorite } = useCharacterStore()

  const favoriteMutation = useMutation({
    mutationFn: async () => {
      const { data } = await client.patch<ApiResponse<Character>>(
        `/characters/${character.id}/favorite`,
      )
      return data.data
    },
    onMutate: () => {
      updateFavorite(character.id, !character.favorite)
      return { previousFavorite: character.favorite }
    },
    onError: (_error, _variables, context) => {
      if (context) updateFavorite(character.id, context.previousFavorite)
    },
    onSuccess: (updated) => {
      updateFavorite(character.id, updated.favorite)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
      void queryClient.invalidateQueries({ queryKey: ['hunting'] })
      void queryClient.invalidateQueries({ predicate: query => String(query.queryKey[0]).startsWith('stats/') })
    },
  })

  return (
    <div className="flex items-center gap-4 rounded-lg bg-[#1a1a2e] px-4 py-3">
      {character.characterImage ? (
        <img
          src={character.characterImage}
          alt={character.characterName}
			className="h-16 w-16 rounded-lg object-cover object-top"
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
        disabled={favoriteMutation.isPending || disabled}
        className="text-xl transition-opacity hover:opacity-70 disabled:opacity-40"
        aria-label={character.favorite ? '즐겨찾기 해제' : '즐겨찾기 추가'}
      >
        {character.favorite ? '★' : '☆'}
      </button>
      {character.favorite && (
        <div className="flex shrink-0 items-center gap-1">
          <button type="button" onClick={onMoveUp} disabled={reorderDisabled || !onMoveUp} title="위로 이동" aria-label="위로 이동"
            className="rounded p-1.5 text-white/70 hover:bg-white/10 disabled:opacity-30">
            <ArrowUp size={17} />
          </button>
          <button type="button" onClick={onMoveDown} disabled={reorderDisabled || !onMoveDown} title="아래로 이동" aria-label="아래로 이동"
            className="rounded p-1.5 text-white/70 hover:bg-white/10 disabled:opacity-30">
            <ArrowDown size={17} />
          </button>
        </div>
      )}
      {favoriteMutation.isError && <span className="text-xs text-[#f87171]">저장 실패</span>}
    </div>
  )
}

// ─── 캐릭터 관리 섹션 ──────────────────────────────────────────────────────────

function CharacterSection() {
  const queryClient = useQueryClient()
  const { characters, setCharacters } = useCharacterStore()
	const [jobId, setJobId] = useState<number | null>(null)
	const [draftOrder, setDraftOrder] = useState<number[] | null>(null)
	const [world, setWorld] = useState('')

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
      const { data } = await client.post<ApiResponse<SyncJob>>('/characters/sync')
      return data.data
    },
    onSuccess: (job) => {
      setJobId(job.id)
    },
  })

  const { data: syncJob, isError: isJobError } = useQuery({
    queryKey: ['characters/sync-jobs', jobId],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SyncJob>>(`/characters/sync-jobs/${jobId}`)
      return data.data
    },
    enabled: jobId !== null,
    refetchInterval: (query) => query.state.data?.status === 'STARTED' ? 2000 : false,
  })

  useEffect(() => {
    if (syncJob?.status === 'COMPLETED') {
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
    }
  }, [syncJob?.status, queryClient])

  const favorites = characters.filter(c => c.favorite).sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const savedOrder = favorites.map(c => c.id)
  const currentOrder = draftOrder ?? savedOrder
  const favoriteById = new Map(favorites.map(c => [c.id, c]))
  const orderedFavorites = currentOrder.map(id => favoriteById.get(id)).filter((c): c is Character => c !== undefined)
  const remaining = characters.filter(c => !c.favorite).sort((a, b) =>
    (b.characterLevel ?? -1) - (a.characterLevel ?? -1) || a.id - b.id,
  )
	const sorted = [...orderedFavorites, ...remaining].filter((character) => !world || character.worldName === world)
	const worlds = [...new Set(characters.map((character) => character.worldName).filter((value): value is string => Boolean(value)))].sort()
  const isDirty = draftOrder !== null && draftOrder.some((id, index) => id !== savedOrder[index])

  const saveOrder = useMutation({
    mutationFn: async (characterIds: number[]) => {
      const { data } = await client.patch<ApiResponse<Character[]>>('/characters/favorites/sort-order', { characterIds })
      return data.data
    },
    onSuccess: () => {
      setDraftOrder(null)
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
      void queryClient.invalidateQueries({ queryKey: ['scheduler/summary'] })
    },
  })

  const move = (index: number, direction: -1 | 1) => {
    const next = [...currentOrder]
    const other = index + direction
    ;[next[index], next[other]] = [next[other], next[index]]
    setDraftOrder(next)
  }

  return (
    <section className="rounded-xl bg-[#2d2d44] p-6">
		<div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-white">
          캐릭터 관리
          {characters.length > 0 && (
            <span className="ml-2 text-sm font-normal text-white/40">{characters.length}명</span>
          )}
        </h2>
		<div className="flex items-center gap-2">
		<button
          onClick={() => syncMutation.mutate()}
          disabled={isDirty || syncMutation.isPending || syncJob?.status === 'STARTED'}
			className="flex items-center gap-2 rounded-lg border border-white/10 px-3 py-1.5 text-sm text-white/70 transition-colors hover:border-white/20 hover:text-white disabled:opacity-40"
        >
          {syncMutation.isPending || syncJob?.status === 'STARTED' ? (
            <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white/80" />
          ) : (
			<RefreshCw size={16} aria-hidden="true" />
			)}
			재동기화
		</button>
		<button type="button" onClick={() => saveOrder.mutate(currentOrder)} disabled={!isDirty || saveOrder.isPending}
			title="즐겨찾기 순서 저장" aria-label="즐겨찾기 순서 저장"
			className="rounded border border-white/20 bg-[#4ade80] p-2 text-[#1a1a2e] disabled:opacity-40">
			<Save size={18} />
		</button>
		</div>
		</div>
		<select value={world} onChange={(event) => setWorld(event.target.value)} className="mb-4 rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white">
			<option value="">전체 월드</option>
			{worlds.map((name) => <option key={name} value={name}>{name}</option>)}
		</select>

      {syncJob?.status === 'STARTED' && (
        <p className="mb-3 text-sm text-white/60">
          동기화 중 {syncJob.totalCount > 0 ? `${syncJob.completedCount}/${syncJob.totalCount}` : ''}
        </p>
      )}
      {syncJob?.status === 'COMPLETED' && syncJob.skippedCount > 0 && (
        <p className="mb-3 text-sm text-white/60">상세 조회 제외 {syncJob.skippedCount}명</p>
      )}

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
            <CharacterRow key={char.id} character={char} disabled={isDirty || saveOrder.isPending}
              reorderDisabled={saveOrder.isPending}
              onMoveUp={char.favorite && currentOrder.indexOf(char.id) > 0
                ? () => move(currentOrder.indexOf(char.id), -1) : undefined}
              onMoveDown={char.favorite && currentOrder.indexOf(char.id) < currentOrder.length - 1
                ? () => move(currentOrder.indexOf(char.id), 1) : undefined} />
          ))}
          {saveOrder.isError && <p className="text-sm text-[#f87171]">순서를 저장하지 못했습니다.</p>}
        </div>
      )}

      {(syncMutation.isError || isJobError || syncJob?.status === 'FAILED') && (
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
