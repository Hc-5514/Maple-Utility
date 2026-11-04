import { useEffect, useRef, useState } from 'react'
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

// 결정 가격 입력 상한(1,000억 메소, 백엔드 검증과 동일)
const MAX_CRYSTAL_PRICE = 100_000_000_000

function PencilIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="m15 5 4 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

// 인원 분배 후 결정 메소를 연필 아이콘으로 수정. Enter·포커스 이동 시 확정, Esc는 취소
function CrystalAmount({ amount, unknownPrice, maxAmount, onCommit }: {
  amount: number | null
  unknownPrice: boolean
  maxAmount: number
  onCommit: (amount: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) input.current?.select()
  }, [editing])

  const commit = () => {
    setEditing(false)
    if (text === '') return
    const next = Math.min(Number(text), maxAmount)
    if (next !== (amount ?? 0)) onCommit(next)
  }

  return (
    <>
      <button
        type="button"
        aria-label="결정 금액 수정"
        onMouseDown={(event) => { if (editing) event.preventDefault() }}
        onClick={() => {
          if (editing) {
            commit()
            return
          }
          setText(amount ? String(amount) : '')
          setEditing(true)
        }}
        className={`flex size-7 shrink-0 items-center justify-center rounded-lg ${editing ? 'bg-[var(--dash-accent)]/12 text-[var(--dash-accent)]' : '-mx-[6px] text-[var(--dash-muted)] hover:text-[var(--dash-accent)]'}`}
      >
        <PencilIcon />
      </button>
      {editing ? (
        <input
          ref={input}
          inputMode="numeric"
          aria-label="결정 금액 입력"
          value={text === '' ? '' : Number(text).toLocaleString()}
          onChange={(event) => setText(event.target.value.replace(/\D/g, '').slice(0, 12))}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') commit()
            if (event.key === 'Escape') {
              event.stopPropagation()
              setEditing(false)
            }
          }}
          className="h-[34px] w-[118px] rounded-lg border border-[var(--dash-accent)] bg-[var(--dash-card)] px-[10px] text-right text-[13px] text-[var(--dash-text)] outline-none [font-family:Inter,sans-serif]"
        />
      ) : (
        <span
          className="text-[13px] text-[var(--dash-text)] [font-family:Inter,sans-serif]"
          aria-label="결정 금액"
        >
          {amount?.toLocaleString() ?? (unknownPrice ? '가격 미확인' : '')}
        </span>
      )}
    </>
  )
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

  // 입력한 인원 분배 금액을 결정 가격(금액 × 인원)으로 환산해 저장
  const changeCrystalAmount = (amount: number) => {
    if (!period) return
    onChange({
      ...period,
      crystalPrice: amount * period.partySize,
      dirty: true,
      items: period.items.map((item) => item.dropItem.itemKind === 'CRYSTAL'
        ? { ...item, mesoAmount: amount > 0 ? amount : null }
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
                <div className="ml-auto flex items-center gap-3 text-[12px] text-[var(--dash-muted)]">
                  수량
                  <PeriodMenu
                    compact
                    label={`${item.dropItem.itemName} 수량`}
                    value={String(item.quantity)}
                    options={Array.from({ length: Math.max(item.dropItem.defaultQuantity, item.quantity) }, (_, index) => index + 1)
                      .map((count) => ({ value: String(count), label: String(count) }))}
                    onChange={(value) => changeItem(item.dropItem.id, { quantity: Number(value) })}
                  />
                </div>
              )}
              {item.dropItem.itemKind === 'CRYSTAL' && (
                <div className="ml-auto flex items-center gap-3 text-[12px] text-[var(--dash-muted)]">
                  <CrystalAmount
                    amount={item.mesoAmount}
                    unknownPrice={period.crystalPrice === 0}
                    maxAmount={Math.floor(MAX_CRYSTAL_PRICE / period.partySize)}
                    onCommit={changeCrystalAmount}
                  />
                  메소
                </div>
              )}
              {item.acquired && item.dropItem.detailOptions.length > 0 && (
                <div className="ml-auto shrink-0">
                  <PeriodMenu
                    compact
                    label={`${item.dropItem.itemName} 상세`}
                    value={item.detailItem ?? ''}
                    placeholder="상세 선택"
                    options={item.dropItem.detailOptions.map((option) => ({ value: option, label: option }))}
                    onChange={(value) => changeItem(item.dropItem.id, { detailItem: value })}
                  />
                </div>
              )}
              <span className={item.dropItem.itemKind === 'RANDOM' && !(item.acquired && item.dropItem.detailOptions.length > 0) ? 'ml-auto' : ''}>
                <ThemedCheckbox
                  checked={item.acquired}
                  onChange={(acquired) => changeItem(item.dropItem.id, acquired ? { acquired } : { acquired, detailItem: null })}
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
