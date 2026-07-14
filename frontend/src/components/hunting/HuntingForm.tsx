import { useState } from 'react'
import CharacterSelector from '../common/CharacterSelector'
import type { HuntingRecord } from '../../types'

interface FormData {
  characterId: number
  recordDate: string
  mesoEarned: number
  solErdaEarned: number
  playDurationMin: number | null
  huntingGround: string | null
  memo: string | null
}

interface Props {
  initialValues?: Partial<HuntingRecord>
  onSubmit: (data: FormData) => void
  isSubmitting: boolean
  submitLabel: string
}

const today = new Date().toISOString().split('T')[0]

export default function HuntingForm({ initialValues, onSubmit, isSubmitting, submitLabel }: Props) {
  const [characterId, setCharacterId] = useState<number | null>(initialValues?.characterId ?? null)
  const [recordDate, setRecordDate] = useState(initialValues?.recordDate ?? today)
  const [mesoEarned, setMesoEarned] = useState(initialValues?.mesoEarned ?? 0)
  const [solErdaEarned, setSolErdaEarned] = useState(initialValues?.solErdaEarned ?? 0)
  const [playDurationMin, setPlayDurationMin] = useState<string>(
    initialValues?.playDurationMin != null ? String(initialValues.playDurationMin) : '',
  )
  const [huntingGround, setHuntingGround] = useState(initialValues?.huntingGround ?? '')
  const [memo, setMemo] = useState(initialValues?.memo ?? '')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!characterId) {
      setError('캐릭터를 선택해 주세요.')
      return
    }
    if (mesoEarned <= 0 && solErdaEarned <= 0) {
      setError('메소 또는 솔 에르다 조각 중 최소 하나를 입력해 주세요.')
      return
    }
    setError(null)
    onSubmit({
      characterId,
      recordDate,
      mesoEarned,
      solErdaEarned,
      playDurationMin: playDurationMin !== '' ? Number(playDurationMin) : null,
      huntingGround: huntingGround.trim() || null,
      memo: memo.trim() || null,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <div className="rounded-xl bg-[#2d2d44] p-6 space-y-5">
        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">
              캐릭터 <span className="text-[#f87171]">*</span>
            </label>
            <CharacterSelector
              selectedId={characterId}
              onChange={(id) => setCharacterId(id)}
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">
              날짜 <span className="text-[#f87171]">*</span>
            </label>
            <input
              type="date"
              value={recordDate}
              max={today}
              onChange={(e) => setRecordDate(e.target.value)}
              required
              className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white [color-scheme:dark] focus:border-[#4ade80]/50 focus:outline-none"
            />
          </div>
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">
              메소 획득
            </label>
            <input
              type="number"
              min={0}
              value={mesoEarned}
              onChange={(e) => setMesoEarned(Number(e.target.value))}
              className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white focus:border-[#4ade80]/50 focus:outline-none"
              placeholder="0"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">
              솔 에르다 조각
            </label>
            <input
              type="number"
              min={0}
              value={solErdaEarned}
              onChange={(e) => setSolErdaEarned(Number(e.target.value))}
              className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white focus:border-[#4ade80]/50 focus:outline-none"
              placeholder="0"
            />
          </div>
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">사냥터</label>
            <input
              type="text"
              value={huntingGround}
              onChange={(e) => setHuntingGround(e.target.value)}
              className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white focus:border-[#4ade80]/50 focus:outline-none"
              placeholder="예: 아르카나"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-white/70">
              플레이 시간 (분)
            </label>
            <input
              type="number"
              min={1}
              value={playDurationMin}
              onChange={(e) => setPlayDurationMin(e.target.value)}
              className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white focus:border-[#4ade80]/50 focus:outline-none"
              placeholder="분 단위"
            />
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-sm font-medium text-white/70">메모</label>
          <textarea
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            rows={3}
            className="w-full rounded border border-white/20 bg-[#1a1a2e] px-3 py-2 text-sm text-white focus:border-[#4ade80]/50 focus:outline-none resize-none"
            placeholder="선택 메모"
          />
        </div>

        {error && (
          <p className="text-sm text-[#f87171]">{error}</p>
        )}
      </div>

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded-lg bg-[#4ade80] px-6 py-2.5 text-sm font-semibold text-[#1a1a2e] transition-opacity hover:opacity-80 disabled:opacity-50"
        >
          {isSubmitting ? '처리 중...' : submitLabel}
        </button>
      </div>
    </form>
  )
}
