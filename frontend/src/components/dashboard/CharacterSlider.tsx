import { useState } from 'react'
import { Link } from 'react-router-dom'
import CharacterSummaryCard from './CharacterSummaryCard'
import type { CharacterSchedulerSummary } from '../../types'

const PAGE_SIZE = 6

interface Props {
  characters: CharacterSchedulerSummary[]
}

export default function CharacterSlider({ characters }: Props) {
  const [page, setPage] = useState(0)

  if (characters.length === 0) {
    return (
      <div className="rounded-xl bg-[#2d2d44] px-8 py-16 text-center">
        <p className="text-2xl">⭐</p>
        <p className="mt-3 font-semibold text-white">즐겨찾기 캐릭터가 없습니다</p>
        <p className="mt-1 text-sm text-white/50">
          설정 페이지에서 캐릭터를 즐겨찾기로 등록하면 여기에 표시됩니다.
        </p>
        <Link
          to="/settings"
          className="mt-4 inline-block rounded-lg bg-[#4ade80] px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90"
        >
          설정으로 이동
        </Link>
      </div>
    )
  }

  const totalPages = Math.ceil(characters.length / PAGE_SIZE)
  const slice = characters.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {slice.map((char) => (
          <CharacterSummaryCard key={char.characterId} character={char} />
        ))}
      </div>

      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded-lg border border-white/10 px-3 py-1.5 text-sm text-white/70 transition-colors hover:border-white/20 hover:text-white disabled:opacity-30"
          >
            &lt;
          </button>
          <span className="text-sm text-white/50">
            {page + 1}/{totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page === totalPages - 1}
            className="rounded-lg border border-white/10 px-3 py-1.5 text-sm text-white/70 transition-colors hover:border-white/20 hover:text-white disabled:opacity-30"
          >
            &gt;
          </button>
        </div>
      )}
    </div>
  )
}
