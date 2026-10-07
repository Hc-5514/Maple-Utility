import { useEffect } from 'react'
import Modal from '../common/Modal'
import DifficultyBadge from '../common/DifficultyBadge'
import CatalogImage from '../common/CatalogImage'
import { useBossPeriod } from '../../hooks/useCharacterDetail'
import type { BossPeriod, BossPeriodDraft, SchedulerBossRecord } from '../../types'

interface Props {
  isOpen: boolean
  onClose: () => void
  record: SchedulerBossRecord
  characterId: number
  periodStart: string
  draft?: BossPeriodDraft
  onLoaded: (period: BossPeriod) => void
  onChange: (draft: BossPeriodDraft) => void
}

export default function BossDropModal({
  isOpen, onClose, record, characterId, periodStart, draft, onLoaded, onChange,
}: Props) {
  const { data, isLoading, isError } = useBossPeriod(characterId, record.bossId ?? 0, periodStart)

  useEffect(() => {
    if (data) onLoaded(data)
  }, [data, onLoaded])

  const period = draft ?? (data ? { ...data, dirty: false } : undefined)

  const changePartySize = (partySize: number) => {
    if (!period || partySize < 1 || partySize > 6) return
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
    <Modal isOpen={isOpen} onClose={onClose} title={`${record.bossName} 드랍 아이템`}>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <CatalogImage src={record.bossImage} alt={record.bossName ?? '보스'} kind="boss" />
          <div>
            <p className="font-semibold text-white">{record.bossName}</p>
            {record.difficulty && <DifficultyBadge difficulty={record.difficulty} />}
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-white/70">
          파티 인원
          <select
            value={period?.partySize ?? 1}
            onChange={(event) => changePartySize(Number(event.target.value))}
            disabled={!period}
            className="rounded border border-white/20 bg-[#1a1a2e] px-2 py-1 text-white"
          >
            {[1, 2, 3, 4, 5, 6].map((size) => <option key={size} value={size}>{size}명</option>)}
          </select>
        </label>
      </div>

      {isLoading ? (
        <div className="h-40 animate-pulse rounded bg-white/10" />
      ) : isError ? (
        <p className="text-sm text-[#f87171]">드랍 아이템을 불러오지 못했습니다.</p>
      ) : !period || period.items.length === 0 ? (
        <p className="text-sm text-white/50">드랍 아이템 정보 없음</p>
      ) : (
        <ul className="max-h-[60vh] space-y-1 overflow-y-auto">
          {period.items.map((item) => (
            <li key={item.dropItem.id} className="flex flex-wrap items-center gap-3 border-b border-white/10 py-3 last:border-0">
              <CatalogImage src={item.dropItem.itemImage} alt={item.dropItem.itemName} kind="item" className="h-8 w-8" />
              <div className="min-w-0 flex-1">
                <p className="text-sm text-white">{item.dropItem.itemName}</p>
                {item.dropItem.itemKind === 'CRYSTAL' && period.crystalPrice === 0 && (
                  <span className="text-xs text-amber-300">가격 미확인</span>
                )}
              </div>
              {item.dropItem.itemKind === 'FIXED' && (
                <label className="flex items-center gap-1 text-xs text-white/60">
                  수량
                  <input
                    type="number"
                    min={1}
                    value={item.quantity}
                    onChange={(event) => changeItem(item.dropItem.id, { quantity: Math.max(1, Number(event.target.value) || 1) })}
                    className="w-16 rounded border border-white/20 bg-[#1a1a2e] px-2 py-1 text-right text-white"
                    aria-label={`${item.dropItem.itemName} 수량`}
                  />
                </label>
              )}
              {item.dropItem.itemKind === 'CRYSTAL' && (
                <label className="flex items-center gap-1 text-xs text-white/60">
                  메소
                  <input
                    type="number"
                    min={0}
                    value={item.mesoAmount ?? ''}
                    placeholder={period.crystalPrice === 0 ? '가격 미확인' : undefined}
                    onChange={(event) => changeItem(item.dropItem.id, {
                      mesoAmount: event.target.value === '' ? null : Math.max(0, Math.floor(Number(event.target.value))),
                    })}
                    className="w-28 rounded border border-white/20 bg-[#1a1a2e] px-2 py-1 text-right text-white"
                    aria-label="결정 금액"
                  />
                </label>
              )}
              <input
                type="checkbox"
                checked={item.acquired}
                onChange={(event) => changeItem(item.dropItem.id, { acquired: event.target.checked })}
                className="h-4 w-4 shrink-0 cursor-pointer accent-[#4ade80]"
                aria-label={`${item.dropItem.itemName} 획득 여부`}
              />
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
