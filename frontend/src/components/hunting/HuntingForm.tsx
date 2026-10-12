import { useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import GlassSelect from '../common/GlassSelect'
import client from '../../api/client'
import { worldNames } from '../dashboard/worlds'
import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from './assets'
import type { ApiResponse, Character, HuntingRecord } from '../../types'
import { useHuntingGrounds, useLatestHuntingGround } from '../../hooks/useHunting'
import { localDate } from '../../utils/date'

interface FormData {
  characterId: number
  recordDate: string
  mesoEarned: number
  solErdaEarned: number
  playDurationMin: number | null
  huntingGround: string | null
  huntingGroundId: number | null
  memo: string | null
}

interface Props {
  initialValues?: Partial<HuntingRecord>
  onSubmit: (data: FormData) => void
  isSubmitting: boolean
  submitLabel: string
}

const today = localDate(new Date())
const inputBox =
  'flex h-12 w-full items-center gap-3 rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-4 focus-within:border-[var(--dash-accent)]'
const inputText =
  'min-w-0 flex-1 bg-transparent text-[14px] leading-5 text-[var(--dash-text)] outline-none placeholder:text-[var(--dash-muted)]'

function Field({ label, htmlFor, children }: { label: string; htmlFor?: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-2">
      <label htmlFor={htmlFor} className="text-[14px] leading-5 font-medium">
        {label}
      </label>
      {children}
    </div>
  )
}

function NumberField({
  id,
  label,
  unit,
  value,
  placeholder,
  onChange,
}: {
  id: string
  label: string
  unit: string
  value: string
  placeholder?: string
  onChange: (value: string) => void
}) {
  return (
    <Field label={label} htmlFor={id}>
      <div className={inputBox}>
        <input
          id={id}
          type="number"
          inputMode="numeric"
          min={0}
          value={value}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
          className={`${inputText} [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none`}
        />
        <span className="shrink-0 text-[13px] text-[var(--dash-muted)]">{unit}</span>
      </div>
    </Field>
  )
}

export default function HuntingForm({ initialValues, onSubmit, isSubmitting, submitLabel }: Props) {
  const { theme } = useThemeStore()
  const assets = huntingAssets[theme]
  const editing = initialValues?.characterId != null
  const dateInput = useRef<HTMLInputElement>(null)

  const [world, setWorld] = useState<string | null>(null)
  const [characterId, setCharacterId] = useState<number | null>(initialValues?.characterId ?? null)
  const [recordDate, setRecordDate] = useState(initialValues?.recordDate ?? today)
  const [mesoEarned, setMesoEarned] = useState(String(initialValues?.mesoEarned ?? 0))
  const [solErdaEarned, setSolErdaEarned] = useState(String(initialValues?.solErdaEarned ?? 0))
  const [playDurationMin, setPlayDurationMin] = useState(
    initialValues?.playDurationMin != null ? String(initialValues.playDurationMin) : '',
  )
  const [groundChoice, setGroundChoice] = useState<{ region: string | null; groundId: number | null } | null>(
    editing ? { region: initialValues?.regionName ?? null, groundId: initialValues?.huntingGroundId ?? null } : null,
  )
  const [memo, setMemo] = useState(initialValues?.memo ?? '')
  const [error, setError] = useState<string | null>(null)

  const { data: favorites = [] } = useQuery({
    queryKey: ['characters', 'favorites'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters/favorites').then((response) => response.data.data),
  })
  const { data: grounds = [] } = useHuntingGrounds()
  const { data: latestGround } = useLatestHuntingGround(editing ? null : characterId)

  const selectedCharacter = favorites.find((character) => character.id === characterId)
  const selectedWorld = world ?? selectedCharacter?.worldName ?? null
  const worlds = useMemo(
    () =>
      [
        ...new Set(
          favorites.map((character) => character.worldName).filter((name): name is string => name != null),
        ),
      ].sort(
        (a, b) => worldNames.indexOf(a) - worldNames.indexOf(b),
      ),
    [favorites],
  )
  const worldCharacters = selectedWorld
    ? favorites.filter((character) => character.worldName === selectedWorld)
    : favorites
  const regions = useMemo(() => [...new Set(grounds.map((ground) => ground.regionName))], [grounds])
  // 사용자가 지역·사냥터를 고르기 전에는 캐릭터의 최근 사냥터를 기본값으로 사용
  const region = groundChoice ? groundChoice.region : (latestGround?.regionName ?? null)
  const groundId = groundChoice ? groundChoice.groundId : (latestGround?.id ?? null)
  const regionalGrounds = grounds.filter((ground) => ground.regionName === region)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!characterId) {
      setError('캐릭터를 선택해 주세요.')
      return
    }
    const meso = mesoEarned === '' ? 0 : Number(mesoEarned)
    const solErda = solErdaEarned === '' ? 0 : Number(solErdaEarned)
    if (meso <= 0 && solErda <= 0) {
      setError('메소 또는 솔 에르다 조각 중 최소 하나를 입력해 주세요.')
      return
    }
    setError(null)
    onSubmit({
      characterId,
      recordDate,
      mesoEarned: meso,
      solErdaEarned: solErda,
      playDurationMin: playDurationMin !== '' ? Number(playDurationMin) : null,
      huntingGroundId: groundId,
      huntingGround: groundId === null ? null : (grounds.find((ground) => ground.id === groundId)?.mapName ?? null),
      memo: memo.trim() || null,
    })
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="relative z-10 flex flex-col gap-6 rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] p-[31px] shadow-[var(--dash-card-shadow)] backdrop-blur-[12px]"
    >
      <div className="flex flex-wrap gap-6 md:flex-nowrap">
        <Field label="월드 선택">
          <GlassSelect
            variant="field"
            label="월드 선택"
            placeholder="월드 선택"
            value={selectedWorld}
            disabled={editing}
            options={[{ value: null, label: '월드 선택' }, ...worlds.map((name) => ({ value: name, label: name }))]}
            onChange={(name) => {
              setWorld(name)
              if (selectedCharacter && selectedCharacter.worldName !== name) setCharacterId(null)
            }}
          />
        </Field>
        <Field label="캐릭터 선택">
          <GlassSelect
            variant="field"
            label="캐릭터 선택"
            placeholder="캐릭터 선택"
            value={characterId}
            disabled={editing}
            options={[
              { value: null, label: '캐릭터 선택' },
              ...worldCharacters.map((character) => ({ value: character.id, label: character.characterName })),
            ]}
            onChange={(id) => {
              setCharacterId(id)
              const character = favorites.find((item) => item.id === id)
              if (character) setWorld(character.worldName)
            }}
          />
        </Field>
      </div>

      <div className="flex flex-wrap gap-6 md:flex-nowrap">
        <Field label="날짜" htmlFor="hunting-record-date">
          <div className="relative">
            <button
              type="button"
              aria-label={`날짜 ${recordDate}`}
              onClick={() => {
                if (typeof dateInput.current?.showPicker === 'function') dateInput.current.showPicker()
                else dateInput.current?.focus()
              }}
              className={`${inputBox} text-left text-[14px] leading-5`}
            >
              <span className="flex-1">{recordDate.replace(/-/g, '.')}</span>
              <img src={assets.calendar} alt="" />
            </button>
            <input
              ref={dateInput}
              id="hunting-record-date"
              type="date"
              tabIndex={-1}
              value={recordDate}
              max={today}
              onChange={(event) => event.target.value && setRecordDate(event.target.value)}
              className="pointer-events-none absolute inset-0 opacity-0"
            />
          </div>
        </Field>
        <Field label="지역">
          <GlassSelect
            variant="field"
            label="지역"
            placeholder="지역 선택"
            value={region}
            options={[{ value: null, label: '지역 선택' }, ...regions.map((name) => ({ value: name, label: name }))]}
            onChange={(name) => setGroundChoice({ region: name, groundId: null })}
          />
        </Field>
        <Field label="사냥터">
          <GlassSelect
            variant="field"
            label="사냥터"
            placeholder="사냥터 선택"
            value={groundId}
            options={[
              { value: null, label: '사냥터 선택' },
              ...regionalGrounds.map((ground) => ({
                value: ground.id,
                label: `${ground.mapName} · Lv.${ground.maxMonsterLevel}`,
              })),
            ]}
            onChange={(id) => setGroundChoice({ region, groundId: id })}
          />
        </Field>
      </div>

      <div className="flex flex-wrap gap-6 md:flex-nowrap">
        <NumberField
          id="hunting-play-duration"
          label="플레이 시간 (분)"
          unit="분"
          placeholder="분 단위"
          value={playDurationMin}
          onChange={setPlayDurationMin}
        />
        <NumberField
          id="hunting-sol-erda"
          label="솔 에르다 조각"
          unit="개"
          placeholder="0"
          value={solErdaEarned}
          onChange={setSolErdaEarned}
        />
        <NumberField
          id="hunting-meso"
          label="메소 획득"
          unit="메소"
          placeholder="0"
          value={mesoEarned}
          onChange={setMesoEarned}
        />
      </div>

      <Field label="메모" htmlFor="hunting-memo">
        <textarea
          id="hunting-memo"
          value={memo}
          onChange={(event) => setMemo(event.target.value)}
          placeholder="선택 메모"
          className="h-[92px] w-full resize-none rounded-[10px] border border-[var(--dash-border)] bg-[var(--dash-card)] px-4 py-[13px] text-[14px] leading-5 text-[var(--dash-text)] outline-none placeholder:text-[var(--dash-muted)] focus:border-[var(--dash-accent)]"
        />
      </Field>

      {error && (
        <p role="alert" className="-mt-2 text-[13px] text-[#f87171]">
          {error}
        </p>
      )}

      <div className="h-px bg-[var(--dash-border)]" />
      <div className="flex justify-end gap-3">
        <Link
          to="/hunting"
          className="flex h-12 items-center rounded-[10px] px-6 text-[14px] font-bold text-[var(--dash-muted)]"
        >
          취소
        </Link>
        <button
          type="submit"
          disabled={isSubmitting}
          className="flex h-12 items-center rounded-[10px] border border-white/15 bg-[var(--dash-primary)] px-8 text-[14px] font-bold text-white disabled:opacity-60"
        >
          {isSubmitting ? '처리 중...' : submitLabel}
        </button>
      </div>
    </form>
  )
}
