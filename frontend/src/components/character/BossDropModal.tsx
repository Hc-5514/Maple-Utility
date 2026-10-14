import { useEffect, useRef } from 'react'
import ThemedDialog, { dialogCancel, dialogConfirm } from '../common/ThemedDialog'
import ThemedCheckbox from '../common/ThemedCheckbox'
import DifficultyBadge from '../common/DifficultyBadge'
import CatalogImage from '../common/CatalogImage'
import PeriodMenu from './PeriodMenu'
import { useBossPeriod } from '../../hooks/useCharacterDetail'
import type { BossPeriod, BossPeriodDraft, SchedulerBossRecord } from '../../types'

interface Props {
  onClose: () => void
  record: SchedulerBossRecord
  characterId: number
  periodStart: string
  draft?: BossPeriodDraft
  onLoaded: (period: BossPeriod) => void
  onChange: (draft: BossPeriodDraft) => void
}

const amountInput =
  'h-[34px] rounded-lg border border-[color-mix(in_srgb,var(--dash-accent)_45%,transparent)] bg-[var(--dash-subtle)] px-3 text-right text-[13px] text-[var(--dash-text)] outline-none [font-family:Inter,sans-serif] placeholder:text-[var(--dash-muted)] focus:border-[var(--dash-accent)]'

function digits(value: string) {
  const onlyDigits = value.replace(/[^0-9]/g, '')
  return onlyDigits === '' ? null : Number(onlyDigits)
}

export default function BossDropModal({
  onClose, record, characterId, periodStart, draft, onLoaded, onChange,
}: Props) {
  const { data, isLoading, isError } = useBossPeriod(characterId, record.bossId ?? 0, periodStart)
  const snapshot = useRef(draft)

  useEffect(() => {
    if (data) onLoaded(data)
  }, [data, onLoaded])

  const period = draft ?? (data ? { ...data, dirty: false } : undefined)

  const cancel = () => {
    const original = snapshot.current ?? (data ? { ...data, dirty: false } : undefined)
    if (original && draft && original !== draft) onChange(original)
    onClose()
  }

  const changePartySize = (partySize: number) => {
    if (!period || partySize < 1 || partySize > period.maxPartySize) return
    onChange({
      ...period,
      partySize,
      dirty: true,
      items: period.items.map((item) => item.dropItem.itemKind === 'CRYSTAL'
        ? { ...item, mesoAmount: period.crystalPrice > 0 ? Math.floor(period.crystalPrice / partySize) : null }
        : item),
    })
  }

  const changeItem = (itemId: number, values: Partial<BossPeriod['items'][number]>) => {
    if (!period) return
    onChange({
      ...period,
      dirty: true,
      items: period.items.map((item) => item.dropItem.id === itemId ? { ...item, ...values } : item),
    })
  }

  return (
    <ThemedDialog
      title={`${record.bossName} 드랍 아이템`}
      width={560}
      top={160}
      gap={20}
      onClose={cancel}
      actions={
        <>
          <button type="button" onClick={cancel} className={dialogCancel}>
            취소
          </button>
          <button type="button" onClick={onClose} className={dialogConfirm}>
            저장
          </button>
        </>
      }
    >
      <div className="flex items-center gap-[14px]">
        <CatalogImage src={record.bossImage} alt={record.bossName ?? '보스'} kind="boss" large />
        <div className="flex min-w-0 flex-col items-start gap-[6px]">
          <p className="truncate text-[18px] font-bold">{record.bossName}</p>
          {record.difficulty && <DifficultyBadge difficulty={record.difficulty} />}
        </div>
        <span className="ml-auto text-[13px] text-[var(--dash-muted)]">파티 인원</span>
        <PeriodMenu
          compact
          label="파티 인원"
          value={String(period?.partySize ?? 1)}
          options={Array.from({ length: period?.maxPartySize ?? 6 }, (_, index) => index + 1)
            .map((size) => ({ value: String(size), label: `${size}명` }))}
          onChange={(value) => changePartySize(Number(value))}
        />
      </div>

      {isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-[var(--dash-subtle)]" />
      ) : isError ? (
        <p className="text-[14px] text-[var(--dash-danger)]">드랍 아이템을 불러오지 못했습니다.</p>
      ) : !period || period.items.length === 0 ? (
        <p className="text-[14px] text-[var(--dash-muted)]">드랍 아이템 정보 없음</p>
      ) : (
        // 첫 행 위 구분선은 리스트 상단 border, 마지막 행 아래는 다이얼로그 공용 구분선(음수 마진으로 행 간격과 동일하게 보정)
        <ul className="dialog-scroll -mb-[9px] max-h-[512px] overflow-y-auto border-t border-[var(--dash-border)]">
          {period.items.map((item) => (
            <li key={item.dropItem.id} className="flex h-[57px] items-center gap-3 border-b border-[var(--dash-border)] last:h-14 last:border-0">
              <CatalogImage src={item.dropItem.itemImage} alt={item.dropItem.itemName} kind="item" />
              <div className="flex min-w-0 flex-col gap-[2px]">
                <p className="truncate text-[14px]">{item.dropItem.itemName}</p>
                {item.dropItem.itemKind === 'CRYSTAL' && period.crystalPrice === 0 && (
                  <span className="text-[11px] text-[var(--dash-extreme)]">가격 미확인</span>
                )}
              </div>
              {item.dropItem.itemKind === 'FIXED' && (
                <label className="ml-auto flex items-center gap-3 text-[12px] text-[var(--dash-muted)]">
                  수량
                  <input
                    inputMode="numeric"
                    value={item.quantity.toLocaleString()}
                    onChange={(event) => changeItem(item.dropItem.id, { quantity: Math.max(1, digits(event.target.value) ?? 1) })}
                    className={`${amountInput} w-[72px]`}
                    aria-label={`${item.dropItem.itemName} 수량`}
                  />
                </label>
              )}
              {item.dropItem.itemKind === 'CRYSTAL' && (
                <label className="ml-auto flex items-center gap-3 text-[12px] text-[var(--dash-muted)]">
                  메소
                  <input
                    inputMode="numeric"
                    value={item.mesoAmount?.toLocaleString() ?? ''}
                    placeholder={period.crystalPrice === 0 ? '가격 미확인' : undefined}
                    readOnly
                    className={`${amountInput} w-[132px]`}
                    aria-label="결정 금액"
                  />
                </label>
              )}
              <span className={item.dropItem.itemKind === 'RANDOM' ? 'ml-auto' : ''}>
                <ThemedCheckbox
                  checked={item.acquired}
                  onChange={(acquired) => changeItem(item.dropItem.id, { acquired })}
                  label={`${item.dropItem.itemName} 획득 여부`}
                />
              </span>
            </li>
          ))}
        </ul>
      )}
    </ThemedDialog>
  )
}
