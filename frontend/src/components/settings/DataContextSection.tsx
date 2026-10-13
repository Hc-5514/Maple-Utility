import { useCallback, useState, type CSSProperties } from 'react'
import { dataContextSwitchErrorMessage } from '../../api/authSession'
import {
  useDataContexts,
  useSwitchDataContext,
  type DataContextGroup,
} from '../../hooks/useDataContexts'
import { useUnsavedChanges } from '../../router/UnsavedChangesGuard'
import { useThemeStore } from '../../stores/themeStore'
import CharacterPortrait from '../common/CharacterPortrait'
import ThemedConfirmDialog from '../common/ThemedConfirmDialog'
import { formatVerifiedAt } from './formatVerifiedAt'
import { settingsAssets } from './assets'

const MAX_KEYS_PER_ACCOUNT = 3
const statusColors = {
  light: { ACTIVE: '#16a34a', EXPIRED: '#f87171', INVALID: '#f97316' },
  dark: { ACTIVE: '#3ddc97', EXPIRED: '#f87171', INVALID: '#f97316' },
} as const

export default function DataContextSection() {
  const { theme } = useThemeStore()
  const assets = settingsAssets[theme]
  const { dirty } = useUnsavedChanges()
  const { contexts, groups } = useDataContexts()
  const switchContext = useSwitchDataContext()
  const [target, setTarget] = useState<DataContextGroup | null>(null)
  const closeDialog = useCallback(() => setTarget(null), [])
  const switching = switchContext.isPending
  const targetCredential = target?.credentials[0]

  const confirmSwitch = () => {
    if (!targetCredential) return
    switchContext.mutate(targetCredential.credentialId, { onSettled: () => setTarget(null) })
  }

  return (
    <section
      data-testid="data-context-card"
      className="settings-card settings-context"
      aria-labelledby="data-context-heading"
    >
      <div className="settings-context-heading">
        <div className="settings-key-icon">
          <img src={assets.imgContextUsers} alt="" />
        </div>
        <div className="settings-api-copy settings-context-copy">
          <h2 id="data-context-heading">연결된 넥슨 계정</h2>
          <p>같은 넥슨 계정의 API Key는 모든 데이터를 공유합니다.</p>
        </div>
        {groups.length > 0 && <span className="settings-count">{groups.length}개 계정</span>}
      </div>
      {contexts.isLoading ? (
        <p role="status" className="settings-context-state settings-muted text-sm">
          <img src={assets.imgRefreshCw} alt="" />
          연결된 넥슨 계정을 불러오는 중...
        </p>
      ) : contexts.isError ? (
        <div role="alert" className="settings-context-state settings-context-failed">
          <span>
            <img src={assets.imgContextAlert} alt="" />
            연결된 넥슨 계정을 불러오지 못했습니다.
          </span>
          <button type="button" className="settings-button" onClick={() => void contexts.refetch()}>
            <img src={assets.imgRefreshCw} alt="" />
            다시 시도
          </button>
        </div>
      ) : (
        <>
          <ul className="settings-context-list">
            {groups.map((group) => (
              <li
                key={group.contextId}
                data-testid="data-context-group"
                data-current={group.current}
                className="settings-context-group"
              >
                <div className="settings-context-account">
                  <div
                    className="settings-avatar"
                    style={{ '--avatar-color': '#c4adff' } as CSSProperties}
                  >
                    {group.image ? (
                      <CharacterPortrait src={group.image} alt={group.name} size={40} />
                    ) : (
                      <span aria-hidden="true">🍁</span>
                    )}
                  </div>
                  <div className="settings-character-info">
                    <p className="name truncate">{group.name}</p>
                    {group.detail && <p className="detail truncate">{group.detail}</p>}
                  </div>
                  {group.current ? (
                    <span
                      className="settings-status"
                      style={{ '--status-color': 'var(--dash-accent)' } as CSSProperties}
                    >
                      ● 현재 사용 중
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="settings-button"
                      disabled={switching}
                      aria-label={`${group.name} 계정으로 전환`}
                      onClick={() => {
                        switchContext.reset()
                        setTarget(group)
                      }}
                    >
                      <img src={assets.imgContextSwap} alt="" />
                      {switching && switchContext.variables === group.credentials[0]?.credentialId
                        ? '전환 중...'
                        : '이 계정으로 전환'}
                    </button>
                  )}
                </div>
                <ul className="settings-context-keys">
                  {group.credentials.map((credential) => (
                    <li
                      key={credential.credentialId}
                      data-testid="data-context-key"
                      className="settings-context-key"
                    >
                      <div className="settings-context-key-id">
                        <img src={assets.imgKeyRound} alt="" />
                        <span className="masked-key">••••{credential.keySuffix}</span>
                        <span
                          className="settings-status"
                          style={
                            {
                              '--status-color': statusColors[theme][credential.status],
                            } as CSSProperties
                          }
                        >
                          ● {credential.status}
                        </span>
                        {credential.current && (
                          <span className="settings-context-session">현재 세션</span>
                        )}
                      </div>
                      <div className="settings-auth-date">
                        <span className="settings-muted">마지막 인증일</span>
                        <time>{formatVerifiedAt(credential.lastVerifiedAt)}</time>
                        <img src={assets.imgShieldCheck} alt="" />
                      </div>
                    </li>
                  ))}
                </ul>
                {group.credentials.length > 1 && (
                  <p className="settings-context-note">
                    <img src={assets.imgInfo} alt="" />
                    같은 넥슨 계정의 API Key {group.credentials.length}/{MAX_KEYS_PER_ACCOUNT}개가
                    캐릭터·사냥 기록 등 모든 데이터를 공유합니다. 계정당 최대 {MAX_KEYS_PER_ACCOUNT}
                    개까지 발급할 수 있습니다.
                  </p>
                )}
              </li>
            ))}
          </ul>
          <p className="settings-context-note">
            <img src={assets.imgInfo} alt="" />
            {groups.length > 1
              ? '계정을 전환하면 현재 화면의 데이터가 선택한 계정 기준으로 바뀝니다. 이전 계정의 기록은 삭제되지 않으며 다시 선택하면 복원됩니다.'
              : '다른 넥슨 계정의 API Key를 등록하면 이곳에서 계정을 전환할 수 있습니다.'}
          </p>
          {switching && (
            <p role="status" className="settings-context-switching">
              <img src={assets.imgRefreshCw} alt="" />
              계정을 전환하는 중...
            </p>
          )}
          {switchContext.isError && (
            <p role="alert" className="settings-context-error">
              <img src={assets.imgContextAlert} alt="" />
              {dataContextSwitchErrorMessage(switchContext.error)}
            </p>
          )}
        </>
      )}
      {target && (
        <ThemedConfirmDialog
          title="넥슨 계정 전환"
          confirmLabel="전환"
          busy={switching}
          onCancel={closeDialog}
          onConfirm={confirmSwitch}
        >
          <p className="font-bold text-[var(--dash-text)]">
            {target.name}
            {targetCredential?.keySuffix && ` · API Key ••••${targetCredential.keySuffix}`}
          </p>
          <p className="mt-1">현재 화면의 데이터가 선택한 넥슨 계정의 데이터로 바뀝니다.</p>
          {dirty && (
            <p className="mt-3 flex items-center gap-2">
              <img src={settingsAssets[theme].imgContextWarn} alt="" width={16} height={16} />
              저장하지 않은 캐릭터 변경사항은 전환 시 버려집니다.
            </p>
          )}
        </ThemedConfirmDialog>
      )}
    </section>
  )
}
