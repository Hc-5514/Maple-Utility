import { useState } from 'react'
import { Link } from 'react-router-dom'
import CharacterSummaryCard from './CharacterSummaryCard'
import type { CharacterSchedulerSummary } from '../../types'
const PAGE_SIZE = 6
export default function CharacterSlider({
  characters,
}: {
  characters: CharacterSchedulerSummary[]
}) {
  const [page, setPage] = useState(0)
  if (!characters.length)
    return (
      <div className="rounded-2xl border border-[var(--dash-border)] bg-[var(--dash-card)] px-8 py-16 text-center">
        <p className="font-bold">표시할 캐릭터가 없습니다</p>
        <p className="mt-2 text-sm text-[var(--dash-muted)]">
          선택한 월드의 캐릭터를 설정에서 즐겨찾기로 등록해 주세요.
        </p>
        <Link
          to="/settings"
          className="mt-4 inline-flex min-h-11 items-center rounded-[10px] bg-[var(--dash-accent)] px-4 text-sm font-bold text-[var(--dash-button-text)]"
        >
          설정으로 이동
        </Link>
      </div>
    )
  const totalPages = Math.ceil(characters.length / PAGE_SIZE)
  const currentPage = Math.min(page, totalPages - 1)
  return (
    <div>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {characters
          .slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
          .map((character, index) => (
            <CharacterSummaryCard key={character.characterId} character={character} index={index} />
          ))}
      </div>
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4 text-sm">
          <button
            type="button"
            aria-label="이전 캐릭터"
            onClick={() => setPage(currentPage - 1)}
            disabled={currentPage === 0}
            className="min-h-11 rounded-lg border border-[var(--dash-border)] px-4 disabled:opacity-30"
          >
            &lt;
          </button>
          <span>
            {currentPage + 1}/{totalPages}
          </span>
          <button
            type="button"
            aria-label="다음 캐릭터"
            onClick={() => setPage(currentPage + 1)}
            disabled={currentPage === totalPages - 1}
            className="min-h-11 rounded-lg border border-[var(--dash-border)] px-4 disabled:opacity-30"
          >
            &gt;
          </button>
        </div>
      )}
    </div>
  )
}
