import { useThemeStore } from '../../stores/themeStore'
import { huntingAssets } from '../hunting/assets'

export default function Pagination({
  label,
  page,
  totalPages,
  onPageChange,
}: {
  label: string
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}) {
  const { theme } = useThemeStore()
  const assets = huntingAssets[theme]
  const pageCount = Math.max(1, totalPages)
  const start = Math.max(0, Math.min(page - 2, pageCount - 5))
  const pages = Array.from({ length: Math.min(5, pageCount) }, (_, index) => start + index)
  const step = 'flex h-[34px] items-center gap-[6px] px-3 text-[13px] text-[var(--dash-muted)] disabled:opacity-40'
  return (
    <nav aria-label={label} className="flex items-center justify-center gap-[6px] px-6 pt-6 pb-[23px]">
      <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)} className={step}>
        <img src={assets.left} alt="" />
        이전
      </button>
      {pages.map((index) => (
        <button
          key={index}
          type="button"
          aria-current={index === page ? 'page' : undefined}
          onClick={() => onPageChange(index)}
          className={`flex size-[34px] items-center justify-center rounded-md border text-[13px] [font-family:Inter,sans-serif] ${index === page ? 'border-[var(--dash-accent)]/16 bg-[var(--dash-accent)]/8 font-bold text-[var(--dash-accent)]' : 'border-transparent text-[var(--dash-muted)]'}`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= pageCount - 1}
        onClick={() => onPageChange(page + 1)}
        className={step}
      >
        다음
        <img src={assets.right} alt="" />
      </button>
    </nav>
  )
}
