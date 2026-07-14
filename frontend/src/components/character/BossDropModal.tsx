import { useState } from 'react'
import Modal from '../common/Modal'
import DifficultyBadge from '../common/DifficultyBadge'
import {
  useBossAcquisitions,
  useBossDropItems,
  useCreateAcquisition,
  useDeleteAcquisition,
} from '../../hooks/useCharacterDetail'
import type { BossMaster, SchedulerBossRecord } from '../../types'

interface Props {
  isOpen: boolean
  onClose: () => void
  boss: BossMaster
  record: SchedulerBossRecord
  characterId: number
}

const TIER_COLOR: Record<string, string> = {
  HIGH:   '#fbbf24',
  NORMAL: '#6b7280',
  LOW:    '#9ca3af',
}

const TIER_LABEL: Record<string, string> = {
  HIGH:   '높음',
  NORMAL: '보통',
  LOW:    '낮음',
}

export default function BossDropModal({ isOpen, onClose, boss, characterId }: Props) {
  const today = new Date().toISOString().split('T')[0]
  const [pendingDates, setPendingDates] = useState<Record<number, string>>({})

  const { data: dropItems, isLoading: loadingItems } = useBossDropItems(boss.id)
  const { data: allAcquisitions } = useBossAcquisitions(characterId)
  const createAcq = useCreateAcquisition()
  const deleteAcq = useDeleteAcquisition()

  const dropItemIds = new Set((dropItems ?? []).map((d) => d.id))
  const acquisitionMap = new Map(
    (allAcquisitions ?? [])
      .filter((a) => dropItemIds.has(a.bossDropItemId))
      .map((a) => [a.bossDropItemId, a]),
  )

  const getDate = (itemId: number) => pendingDates[itemId] ?? today

  const handleCheck = (itemId: number, checked: boolean) => {
    if (checked) {
      createAcq.mutate({ characterId, bossDropItemId: itemId, acquiredDate: getDate(itemId) })
    } else {
      const acq = acquisitionMap.get(itemId)
      if (acq) deleteAcq.mutate({ id: acq.id, characterId })
    }
  }

  const handleDateChange = (itemId: number, newDate: string) => {
    setPendingDates((prev) => ({ ...prev, [itemId]: newDate }))
    const acq = acquisitionMap.get(itemId)
    if (acq) {
      deleteAcq.mutate(
        { id: acq.id, characterId },
        {
          onSuccess: () => {
            createAcq.mutate({ characterId, bossDropItemId: itemId, acquiredDate: newDate })
          },
        },
      )
    }
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`${boss.bossName} 드랍 아이템`}>
      <div className="mb-5 flex items-center gap-3">
        {boss.bossImage ? (
          <img
            src={boss.bossImage}
            alt={boss.bossName}
            className="h-16 w-16 rounded-lg object-contain"
          />
        ) : (
          <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-[#1a1a2e] text-3xl">
            🗡️
          </div>
        )}
        <div>
          <p className="font-semibold text-white">{boss.bossName}</p>
          <div className="mt-1 flex items-center gap-2">
            <DifficultyBadge difficulty={boss.difficulty} />
            <span className="text-xs text-white/40">
              {boss.crystalPrice > 0
                ? `${boss.crystalPrice.toLocaleString()} 메소`
                : '무결정'}
            </span>
          </div>
        </div>
      </div>

      {loadingItems ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-white/10" />
          ))}
        </div>
      ) : !dropItems || dropItems.length === 0 ? (
        <p className="text-sm text-white/40">드랍 아이템 정보 없음</p>
      ) : (
        <ul className="max-h-80 space-y-2 overflow-y-auto">
          {dropItems.map((item) => {
            const acq = acquisitionMap.get(item.id)
            const isAcquired = !!acq
            return (
              <li key={item.id} className="rounded-lg bg-[#1a1a2e] px-4 py-3">
                <div className="flex items-center gap-3">
                  {item.itemImage ? (
                    <img
                      src={item.itemImage}
                      alt={item.itemName}
                      className="h-10 w-10 rounded object-contain"
                    />
                  ) : (
                    <div className="flex h-10 w-10 items-center justify-center rounded bg-[#2d2d44] text-xl">
                      🎁
                    </div>
                  )}

                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm text-white/90">{item.itemName}</p>
                    {item.dropRateTier && (
                      <span
                        className="inline-block rounded px-1.5 py-0.5 text-xs"
                        style={{
                          backgroundColor: `${TIER_COLOR[item.dropRateTier] ?? '#6b7280'}20`,
                          color: TIER_COLOR[item.dropRateTier] ?? '#6b7280',
                        }}
                      >
                        {TIER_LABEL[item.dropRateTier] ?? item.dropRateTier}
                      </span>
                    )}
                  </div>

                  <div className="flex shrink-0 items-center gap-2">
                    {isAcquired && (
                      <input
                        type="date"
                        value={acq.acquiredDate}
                        onChange={(e) => handleDateChange(item.id, e.target.value)}
                        className="rounded bg-[#2d2d44] px-2 py-1 text-xs text-white/80 focus:outline-none focus-visible:ring-1 focus-visible:ring-[#4ade80]"
                      />
                    )}
                    <input
                      type="checkbox"
                      checked={isAcquired}
                      onChange={(e) => handleCheck(item.id, e.target.checked)}
                      className="h-4 w-4 cursor-pointer accent-[#4ade80]"
                      aria-label={`${item.itemName} 획득 여부`}
                    />
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Modal>
  )
}
