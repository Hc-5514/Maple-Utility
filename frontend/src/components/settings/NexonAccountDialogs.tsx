import { useState } from 'react'
import {
  breakSentences,
  dataContextAddErrorMessage,
  dataContextDeleteErrorMessage,
  dataContextUnlinkErrorMessage,
} from '../../api/authSession'
import {
  useAddNexonAccount,
  useDeleteDataContextData,
  usePrecheckDataContext,
  useUnlinkDataContext,
  type DataContextGroup,
} from '../../hooks/useDataContexts'
import { useThemeStore } from '../../stores/themeStore'
import ThemedConfirmDialog from '../common/ThemedConfirmDialog'
import { settingsAssets } from './assets'

const BODY_CLASS = 'text-base leading-[1.65] text-[var(--dash-muted)]'

type StatusKind = 'info' | 'error' | 'progress'

function DialogStatus({ kind, text }: { kind: StatusKind; text: string }) {
  const { theme } = useThemeStore()
  const assets = settingsAssets[theme]
  const icon =
    kind === 'progress' ? assets.imgRefreshCw : kind === 'error' ? assets.imgContextAlert : assets.imgContextWarn
  return (
    <p
      role={kind === 'error' ? 'alert' : 'status'}
      data-kind={kind}
      className="settings-dialog-status"
    >
      <img src={icon} alt="" />
      <span>{breakSentences(text)}</span>
    </p>
  )
}

function accountLabel(group: DataContextGroup) {
  const suffix = group.credentials.find((credential) => credential.current)?.keySuffix
    ?? group.credentials[0]?.keySuffix
  return suffix ? `${group.name} · API Key ••••${suffix}` : group.name
}

// 넥슨 계정 추가: 입력 → 연동 가능 여부 사전 확인 → 추가
export function AddNexonAccountDialog({ onClose }: { onClose: () => void }) {
  const [apiKey, setApiKey] = useState('')
  const [blocked, setBlocked] = useState<string | null>(null)
  const precheck = usePrecheckDataContext()
  const add = useAddNexonAccount()
  const busy = precheck.isPending || add.isPending
  const key = apiKey.trim()

  const submit = async () => {
    if (!key || busy) return
    setBlocked(null)
    add.reset()
    try {
      const result = await precheck.mutateAsync(key)
      if (result.status === 'LINKED_TO_OTHER_KAKAO') {
        setBlocked(
          '이미 다른 카카오 계정에 연결된 넥슨 계정입니다. 해당 카카오 계정에서 연결을 해제한 뒤 다시 시도해 주세요.'
        )
        return
      }
      if (result.status === 'LIMIT_EXCEEDED') {
        setBlocked(
          `넥슨 계정은 최대 ${result.maxCount}개까지 연결할 수 있습니다. 기존 계정의 연결을 해제한 뒤 다시 시도해 주세요.`
        )
        return
      }
      await add.mutateAsync(key)
      onClose()
    } catch {
      // 오류 문구는 mutation 상태로 표시
    }
  }

  const failure = precheck.isError ? precheck.error : add.isError ? add.error : null
  const status: { kind: StatusKind; text: string } = busy
    ? {
        kind: 'progress',
        text: precheck.isPending
          ? '연동 가능 여부를 확인하는 중입니다...'
          : '넥슨 계정을 추가하는 중입니다...',
      }
    : blocked
      ? { kind: 'error', text: blocked }
      : failure
        ? { kind: 'error', text: dataContextAddErrorMessage(failure) }
        : {
            kind: 'info',
            text: '이미 다른 카카오 계정에 연결된 넥슨 계정은 추가할 수 없습니다.',
          }

  return (
    <ThemedConfirmDialog
      title="넥슨 계정 추가"
      confirmLabel={failure && !blocked ? '다시 시도' : '추가'}
      busyLabel={precheck.isPending ? '확인 중...' : '추가 중...'}
      busy={busy}
      confirmDisabled={!key || blocked !== null}
      initialFocus="input"
      bodyClassName={BODY_CLASS}
      onCancel={onClose}
      onConfirm={() => void submit()}
    >
      <p>추가할 넥슨 계정의 Nexon Open API Key를 입력해 주세요.</p>
      <input
        className="settings-input mt-3 w-full"
        type="password"
        autoComplete="off"
        aria-label="Nexon API Key"
        placeholder="Nexon Open API Key 입력"
        value={apiKey}
        disabled={busy}
        aria-invalid={blocked !== null}
        onChange={(event) => {
          setApiKey(event.target.value)
          setBlocked(null)
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            void submit()
          }
        }}
      />
      <DialogStatus kind={status.kind} text={status.text} />
    </ThemedConfirmDialog>
  )
}

// 카카오 연결만 해제하고 기록 데이터는 보존
export function UnlinkNexonAccountDialog({
  group,
  onClose,
}: {
  group: DataContextGroup
  onClose: () => void
}) {
  const unlink = useUnlinkDataContext()
  const confirm = () => unlink.mutate(group.contextId, { onSuccess: onClose })
  const status = unlink.isError
    ? { kind: 'error' as const, text: dataContextUnlinkErrorMessage(unlink.error) }
    : group.current
      ? {
          kind: 'info' as const,
          text: '현재 사용 중인 계정입니다. 해제하면 다른 연결 계정으로 전환됩니다.',
        }
      : null
  return (
    <ThemedConfirmDialog
      title="넥슨 계정 연결 해제"
      confirmLabel={unlink.isError ? '다시 시도' : '연결 해제'}
      busyLabel="해제 중..."
      busy={unlink.isPending}
      bodyClassName={BODY_CLASS}
      onCancel={onClose}
      onConfirm={confirm}
    >
      <p className="font-bold text-[var(--dash-text)]">{accountLabel(group)}</p>
      <p className="mt-1">
        이 카카오 계정과의 연결만 해제됩니다. 기록 데이터는 삭제되지 않으며, 다른 카카오 계정에서 다시
        연결할 수 있습니다.
      </p>
      {status && <DialogStatus kind={status.kind} text={status.text} />}
    </ThemedConfirmDialog>
  )
}

// 해당 계정의 모든 API Key와 기록 데이터를 DB에서 영구 삭제
export function DeleteAccountDataDialog({
  group,
  onClose,
  loginNote,
}: {
  group: Pick<DataContextGroup, 'contextId' | 'name' | 'credentials'>
  onClose: () => void
  loginNote?: string
}) {
  const remove = useDeleteDataContextData()
  const confirm = () => remove.mutate(group.contextId, { onSuccess: onClose })
  return (
    <ThemedConfirmDialog
      title="계정 데이터 삭제"
      confirmLabel={remove.isError ? '다시 시도' : '영구 삭제'}
      confirmTone="danger"
      busyLabel="삭제 중..."
      busy={remove.isPending}
      bodyClassName={BODY_CLASS}
      onCancel={onClose}
      onConfirm={confirm}
    >
      <p className="font-bold text-[var(--dash-text)]">{accountLabel(group as DataContextGroup)}</p>
      <p className="mt-1">
        {loginNote ??
          '이 넥슨 계정에 등록된 모든 API Key와 캐릭터·보스·사냥 기록 데이터가 DB에서 영구 삭제됩니다. 삭제한 데이터는 복구할 수 없습니다.'}
      </p>
      <DialogStatus
        kind="error"
        text={
          remove.isError ? dataContextDeleteErrorMessage(remove.error) : '이 작업은 되돌릴 수 없습니다.'
        }
      />
    </ThemedConfirmDialog>
  )
}

