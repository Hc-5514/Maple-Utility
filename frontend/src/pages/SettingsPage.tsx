import { useCallback, useEffect, useState, type CSSProperties } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { apiKeyErrorMessage, installAuthSession } from '../api/authSession'
import { isAxiosError } from 'axios'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import { useThemeStore } from '../stores/themeStore'
import { useSaveWorldSelection, useWorldSelection } from '../hooks/useWorldSelection'
import { useUnsavedChanges } from '../router/UnsavedChangesGuard'
import ThemedConfirmDialog from '../components/common/ThemedConfirmDialog'
import CharacterPortrait from '../components/common/CharacterPortrait'
import WorldSelectionModal from '../components/dashboard/WorldSelectionModal'
import { settingsAssets } from '../components/settings/assets'
import { MAX_CHARACTER_FAVORITES } from '../constants/character'
import type { ApiKeyRegistrationResponse, ApiKeyStatusResponse, ApiResponse, Character, SyncJob } from '../types'

export function formatVerifiedAt(value: string | null) {
  if (!value) return '미인증'
  const date = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : value + '+09:00')
  if (Number.isNaN(date.getTime())) return '미인증'
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type: string) => parts.find((part) => part.type === type)?.value
  return `${get('year')}.${get('month')}.${get('day')} ${get('hour')}:${get('minute')}`
}
function ApiKeySection() {
  const queryClient = useQueryClient()
  const { setHasApiKey } = useAuthStore()
  const { theme } = useThemeStore()
  const assets = settingsAssets[theme]
  const [inputKey, setInputKey] = useState('')
  const [deleting, setDeleting] = useState(false)
  const closeDelete = useCallback(() => setDeleting(false), [])
  const status = useQuery({
    queryKey: ['api-key/status'],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<ApiKeyStatusResponse>>('/api-key/status')
      return data.data
    },
  })
  const register = useMutation({
    mutationFn: async (apiKey: string) => {
      const { data } = await client.post<ApiResponse<ApiKeyRegistrationResponse>>('/api-key', { apiKey })
      return data.data
    },
    onSuccess: async (registration) => {
      setInputKey('')
      await installAuthSession(queryClient, registration?.auth, undefined, true)
      await queryClient.refetchQueries({ type: 'active' })
    },
  })
  const remove = useMutation({
    mutationFn: async () => {
      await client.delete('/api-key')
    },
    onSuccess: () => {
      setHasApiKey(false)
      setDeleting(false)
      void queryClient.invalidateQueries({ queryKey: ['api-key/status'] })
    },
  })
  const registered = status.data?.registered === true
  const keyStatus = status.data?.keyStatus ?? 'INVALID'
  const active = registered && keyStatus === 'ACTIVE'
  const statusColor =
    keyStatus === 'ACTIVE'
      ? theme === 'light'
        ? '#16a34a'
        : '#3ddc97'
      : keyStatus === 'EXPIRED'
        ? '#f87171'
        : '#f97316'
  return (
    <section
      data-testid="api-key-card"
      className="settings-card settings-api"
      aria-labelledby="api-key-heading"
    >
      <div className="settings-api-heading">
        <div className="settings-api-title">
          <div className="settings-key-icon">
            <img src={assets.imgKeyRound} alt="" />
          </div>
          <div className="settings-api-copy">
            <h2 id="api-key-heading">Nexon API Key</h2>
            <p>캐릭터 데이터를 안전하게 불러옵니다.</p>
          </div>
          {registered && (
            <span
              className="settings-status"
              style={{ '--status-color': statusColor } as CSSProperties}
            >
              ● {keyStatus}
            </span>
          )}
        </div>
        {registered && (
          <button
            type="button"
            className="settings-button"
            onClick={() => {
              remove.reset()
              setDeleting(true)
            }}
          >
            <img src={assets.imgTrash2} alt="" />
            삭제
          </button>
        )}
      </div>
      {status.isLoading ? (
        <p role="status" className="settings-muted text-sm">
          API Key 상태를 불러오는 중...
        </p>
      ) : status.isError ? (
        <div role="alert" className="flex flex-wrap items-center gap-4">
          <p className="settings-muted text-sm">API Key 상태를 불러오지 못했습니다.</p>
          <button type="button" className="settings-button" onClick={() => void status.refetch()}>
            다시 시도
          </button>
        </div>
      ) : (
        <>
          {registered && (
            <div data-testid="api-key-credentials" className="settings-credentials">
              <p className="masked-key">
                •••••••• – •••••••• – •••••••• – ••••{status.data?.keySuffix ?? '••••'}
              </p>
              <div className="settings-auth-date">
                <span className="settings-muted">마지막 인증일</span>
                <time>{formatVerifiedAt(status.data?.lastVerifiedAt ?? null)}</time>
                <img src={assets.imgShieldCheck} alt="" />
              </div>
            </div>
          )}
          {!active && (
            <form
              className="flex flex-col gap-3"
              onSubmit={(event) => {
                event.preventDefault()
                if (inputKey.trim()) register.mutate(inputKey.trim())
              }}
            >
              <p className="settings-muted text-sm">
                {registered
                  ? 'API Key가 유효하지 않습니다. 새로운 키를 등록해 주세요.'
                  : '넥슨 게임 데이터를 불러오려면 API Key를 등록해 주세요.'}
              </p>
              <div className="flex gap-3">
                <input
                  className="settings-input"
                  type="password"
                  autoComplete="off"
                  aria-label="Nexon API Key"
                  placeholder="Nexon Open API Key 입력"
                  value={inputKey}
                  onChange={(event) => setInputKey(event.target.value)}
                />
                <button
                  type="submit"
                  className="settings-button settings-primary"
                  disabled={!inputKey.trim() || register.isPending}
                >
                  {register.isPending ? '등록 중...' : '등록'}
                </button>
              </div>
              {register.isError && (
                <p role="alert" className="text-sm text-red-400">
                  {apiKeyErrorMessage(register.error, '등록 중 오류가 발생했습니다. 키를 확인하고 다시 시도해 주세요.')}
                </p>
              )}
            </form>
          )}
        </>
      )}
      {deleting && (
        <ThemedConfirmDialog
          title="API Key 삭제"
          onCancel={closeDelete}
          onConfirm={() => remove.mutate()}
          confirmLabel="삭제"
          busy={remove.isPending}
        >
          <p>API Key를 삭제하면 게임 데이터 조회가 불가능해집니다. 정말 삭제하시겠습니까?</p>
          {remove.isError && (
            <p role="alert" className="mt-3 text-red-400">
              삭제 중 오류가 발생했습니다. 다시 시도해 주세요.
            </p>
          )}
        </ThemedConfirmDialog>
      )}
    </section>
  )
}
export function moveVisibleNeighbor(
  characters: Character[],
  visibleIds: number[],
  id: number,
  direction: -1 | 1
) {
  const otherId = visibleIds[visibleIds.indexOf(id) + direction]
  if (otherId === undefined) return characters
  const next = [...characters]
  const index = next.findIndex((character) => character.id === id),
    otherIndex = next.findIndex((character) => character.id === otherId)
  ;[next[index], next[otherIndex]] = [next[otherIndex], next[index]]
  return next
}
const avatarColors = ['#c4adff', '#7edcc3', '#ffbaa1', '#f6cb7d', '#eaaed4', '#91c4f5']
function CharacterSection() {
  const queryClient = useQueryClient()
  const { setCharacters } = useCharacterStore()
  const { theme } = useThemeStore()
  const assets = settingsAssets[theme]
  const { setDirty } = useUnsavedChanges()
  const [draft, setDraft] = useState<Character[] | null>(null)
  const [jobId, setJobId] = useState<number | null>(null)
  const [syncConfirmation, setSyncConfirmation] = useState(false)
  const closeSyncConfirmation = useCallback(() => setSyncConfirmation(false), [])
  const [reloadConfirmation, setReloadConfirmation] = useState(false)
  const closeReloadConfirmation = useCallback(() => setReloadConfirmation(false), [])
  const [worldModal, setWorldModal] = useState(false)
  const closeWorldModal = useCallback(() => setWorldModal(false), [])
  const worlds = useWorldSelection(),
    saveWorlds = useSaveWorldSelection()
  const characters = useQuery({
    queryKey: ['characters'],
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<Character[]>>('/characters')
      const sorted = [...data.data].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
      setCharacters(sorted)
      return sorted
    },
  })
  const saved = characters.data ?? [],
    current = draft ?? saved
  const favoriteCount = current.filter((character) => character.favorite).length
  const favoriteLimitReached = favoriteCount >= MAX_CHARACTER_FAVORITES
  const favoriteLimitExceeded = favoriteCount > MAX_CHARACTER_FAVORITES
  const dirty =
    current.length !== saved.length ||
    current.some(
      (character, index) =>
        character.id !== saved[index]?.id || character.favorite !== saved[index]?.favorite
    )
  useEffect(() => {
    setDirty(dirty)
    return () => setDirty(false)
  }, [dirty, setDirty])
  const invalidate = () => {
    void queryClient.invalidateQueries({
      predicate: (query) => {
        const key = String(query.queryKey[0])
        return (
          ['characters', 'characters/favorites', 'scheduler/summary', 'hunting'].includes(key) ||
          key.startsWith('stats/')
        )
      },
    })
  }
  const save = useMutation({
    mutationFn: async (list: Character[]) => {
      const { data } = await client.put<ApiResponse<Character[]>>('/characters/preferences', {
        characters: list.map((character) => ({
          characterId: character.id,
          favorite: character.favorite,
        })),
      })
      return data.data
    },
    onSuccess: (list) => {
      queryClient.setQueryData(['characters'], list)
      setCharacters(list)
      setDraft(null)
      invalidate()
    },
  })
  const sync = useMutation({
    mutationFn: async () => {
      const { data } = await client.post<ApiResponse<SyncJob>>('/characters/sync')
      return data.data
    },
    onSuccess: (job) => setJobId(job.id),
  })
  const job = useQuery({
    queryKey: ['characters/sync-jobs', jobId],
    enabled: jobId !== null,
    queryFn: async () => {
      const { data } = await client.get<ApiResponse<SyncJob>>(`/characters/sync-jobs/${jobId}`)
      return data.data
    },
    refetchInterval: (query) => (query.state.data?.status === 'STARTED' ? 2000 : false),
  })
  useEffect(() => {
    if (job.data?.status === 'COMPLETED')
      void queryClient.invalidateQueries({ queryKey: ['characters'] })
  }, [job.data?.status, queryClient])
  const syncing = sync.isPending || job.data?.status === 'STARTED'
  const selectedWorlds = worlds.data?.worldNames ?? []
  const visible = current.filter(
      (character) => character.worldName !== null && selectedWorlds.includes(character.worldName)
    ),
    visibleIds = visible.map((character) => character.id)
  const toggleFavorite = (id: number) => {
    if (favoriteLimitReached && !current.find((character) => character.id === id)?.favorite) return
    save.reset()
    setDraft(
      current.map((character) =>
        character.id === id ? { ...character, favorite: !character.favorite } : character
      )
    )
  }
  const move = (id: number, direction: -1 | 1) => {
    save.reset()
    setDraft(moveVisibleNeighbor(current, visibleIds, id, direction))
  }
  return (
    <section
      data-testid="character-management"
      className="settings-card settings-character-card"
      aria-labelledby="character-management-heading"
    >
      <div className="settings-character-heading">
        <div className="settings-character-title">
          <div>
            <h2 id="character-management-heading">캐릭터 관리</h2>
            <span className="settings-count">{visible.length} 캐릭터</span>
            <span className="settings-count" aria-live="polite" aria-atomic="true">
              즐겨찾기 {favoriteCount}/{MAX_CHARACTER_FAVORITES}
            </span>
          </div>
          <p>즐겨찾기와 표시 순서를 원하는 대로 설정하세요.</p>
        </div>
        <div className="settings-actions">
          {dirty && (
            <p className="dirty">
              <span>●</span> 저장하지 않은 변경사항이 있어요
            </p>
          )}
          <div className="settings-action-buttons">
            <button
              type="button"
              className="settings-button"
              disabled={save.isPending || syncing}
              onClick={() => {
                if (dirty) setSyncConfirmation(true)
                else sync.mutate()
              }}
            >
              <img src={assets.imgRefreshCw} alt="" />
              {syncing ? '새로고침 중...' : '캐릭터 새로고침'}
            </button>
            <button
              type="button"
              className="settings-button"
              disabled={worlds.isLoading || worlds.isError || save.isPending}
              aria-expanded={worldModal}
              onClick={() => setWorldModal(true)}
            >
              <img src={assets.imgControlIcon} alt="" />
              월드 선택
            </button>
            <button
              type="button"
              className="settings-button settings-primary"
              disabled={!dirty || save.isPending || favoriteLimitExceeded}
              aria-describedby={favoriteLimitExceeded ? 'favorite-guidance' : undefined}
              onClick={() => save.mutate(current)}
            >
              {save.isPending ? '저장 중...' : '저장'}
            </button>
          </div>
        </div>
      </div>
      <div data-testid="character-columns" className="settings-columns">
        <p>캐릭터 정보</p>
        <p className="settings-favorite-heading">즐겨찾기</p>
        <p className="settings-order-heading">표시 순서</p>
      </div>
      {characters.isLoading || worlds.isLoading ? (
        <p role="status" className="settings-guidance">
          캐릭터를 불러오는 중...
        </p>
      ) : characters.isError || worlds.isError ? (
        <div role="alert" className="settings-guidance">
          <span>캐릭터 또는 월드 선택을 불러오지 못했습니다.</span>
          <button
            type="button"
            className="settings-button"
            onClick={() => {
              void characters.refetch()
              void worlds.refetch()
            }}
          >
            다시 시도
          </button>
        </div>
      ) : visible.length === 0 ? (
        <p className="settings-guidance">
          {selectedWorlds.length === 0
            ? '월드를 선택하면 해당 월드의 캐릭터가 표시됩니다.'
            : '선택한 월드에 캐릭터가 없습니다. 캐릭터 새로고침으로 불러오세요.'}
        </p>
      ) : (
        visible.map((character, index) => (
          <div
            data-testid="character-row"
            className="settings-row"
            key={character.id}
            data-character-id={character.id}
          >
            <div
              className="settings-avatar"
              style={
                {
                  '--avatar-color': avatarColors[(character.id - 1) % avatarColors.length],
                } as CSSProperties
              }
            >
              {character.characterImage ? (
                <CharacterPortrait
                  src={character.characterImage}
                  alt={character.characterName}
                  size={44}
                />
              ) : (
                <span aria-hidden="true">🍁</span>
              )}
            </div>
            <div className="settings-character-info">
              <p className="name truncate">{character.characterName}</p>
              <p className="detail truncate">
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
              type="button"
              className="settings-favorite"
              aria-label={`${character.characterName} 즐겨찾기 ${character.favorite ? '해제' : '추가'}`}
              aria-pressed={character.favorite}
              disabled={save.isPending || syncing || (!character.favorite && favoriteLimitReached)}
              aria-describedby={favoriteLimitReached ? 'favorite-guidance' : undefined}
              onClick={() => toggleFavorite(character.id)}
            >
              <img src={character.favorite ? assets.imgStar : assets.imgStar1} alt="" />
            </button>
            <div className="settings-order">
              <button
                type="button"
                aria-label={`${character.characterName} 위로 이동`}
                disabled={index === 0 || save.isPending || syncing}
                onClick={() => move(character.id, -1)}
              >
                <img src={assets.imgArrowUp} alt="" />
              </button>
              <button
                type="button"
                aria-label={`${character.characterName} 아래로 이동`}
                disabled={index === visible.length - 1 || save.isPending || syncing}
                onClick={() => move(character.id, 1)}
              >
                <img src={assets.imgArrowDown} alt="" />
              </button>
            </div>
          </div>
        ))
      )}
      {save.isError && (
        <p role="alert" className="settings-error">
          {isAxiosError(save.error) && save.error.response?.data?.code === 'FAVORITE_LIMIT_EXCEEDED'
            ? '즐겨찾기는 전체 월드 합산 최대 18명까지 선택할 수 있습니다. 최신 목록을 확인하고 다시 저장해 주세요.'
            : '변경사항을 저장하지 못했습니다. 다시 시도해 주세요.'}
        </p>
      )}
      {(sync.isError || job.isError || job.data?.status === 'FAILED') && (
        <p role="alert" className="settings-error">
          캐릭터 새로고침 중 오류가 발생했습니다.
        </p>
      )}
      {job.data?.status === 'STARTED' && (
        <p role="status" className="settings-guidance">
          동기화 중 {job.data.completedCount}/{job.data.totalCount}
        </p>
      )}
      {job.data?.status === 'COMPLETED' && job.data.skippedCount > 0 && (
        <p className="settings-guidance">상세 조회 제외 {job.data.skippedCount}명</p>
      )}
      <div
        id="favorite-guidance"
        className={`settings-guidance${favoriteLimitExceeded ? ' settings-favorite-over-limit' : ''}`}
        role={favoriteLimitExceeded ? 'alert' : undefined}
      >
        <img src={assets.imgInfo} alt="" />
        <p>
          {favoriteLimitExceeded
            ? `즐겨찾기가 ${favoriteCount}명입니다. 18명 이하로 줄인 뒤 저장해 주세요. 숨겨진 캐릭터는 월드 선택에서 확인할 수 있습니다.`
            : favoriteLimitReached
              ? '전체 월드 합산 18명을 선택했습니다. 즐겨찾기를 해제하면 다른 캐릭터를 추가할 수 있습니다.'
              : '즐겨찾기는 전체 월드 합산 최대 18명까지 선택할 수 있으며, 사냥 기록과 통계의 캐릭터 필터에서 빠르게 선택할 수 있습니다.'}
        </p>
      </div>
      {isAxiosError(save.error) && save.error.response?.status === 409 && (
        <div className="settings-guidance">
          <span>캐릭터 목록이 변경되었습니다. 최신 목록을 불러와 다시 설정해 주세요.</span>
          <button
            type="button"
            className="settings-button"
            onClick={() => setReloadConfirmation(true)}
          >
            최신 목록 불러오기
          </button>
        </div>
      )}
      {syncConfirmation && (
        <ThemedConfirmDialog
          title="캐릭터 새로고침"
          onCancel={closeSyncConfirmation}
          confirmLabel="변경사항 버리고 새로고침"
          onConfirm={() => {
            setDraft(null)
            save.reset()
            setSyncConfirmation(false)
            sync.mutate()
          }}
        >
          저장하지 않은 변경을 버리고 캐릭터를 새로고침할까요?
        </ThemedConfirmDialog>
      )}
      {reloadConfirmation && (
        <ThemedConfirmDialog
          title="최신 캐릭터 목록 불러오기"
          onCancel={closeReloadConfirmation}
          confirmLabel="변경사항 버리고 새로고침"
          onConfirm={() => {
            setDraft(null)
            save.reset()
            setReloadConfirmation(false)
            void characters.refetch()
          }}
        >
          저장하지 않은 변경사항을 버리고 최신 목록을 불러오시겠습니까?
        </ThemedConfirmDialog>
      )}
      {worldModal && (
        <WorldSelectionModal
          placement="settings"
          selectedWorlds={selectedWorlds}
          onClose={closeWorldModal}
          onSave={(names) => saveWorlds.mutateAsync(names)}
        />
      )}
    </section>
  )
}
export default function SettingsPage() {
  const sessionRevision = useAuthStore((state) => state.sessionRevision)
  return (
    <div className="settings-page">
      <div data-testid="settings-heading" className="settings-heading">
        <p className="eyebrow">MY MAPLE / PREFERENCES</p>
        <h1>설정</h1>
        <p className="settings-muted text-[14px] leading-[1.45]">
          API 연결과 내 캐릭터를 관리하세요. 나에게 맞는 메이플의 시작.
        </p>
      </div>
      <ApiKeySection key={`key-${sessionRevision}`} />
      <CharacterSection key={`characters-${sessionRevision}`} />
    </div>
  )
}
