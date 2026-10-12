import { useState } from 'react'
import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from '../character/assets'

// Add public/assets paths here together with their files. Unknown local URLs are never requested.
const LOCAL_ASSET_PATHS = new Set<string>([])

export function catalogImageSrc(value: string | null | undefined): string | null {
  if (!value) return null
  if (value.startsWith('/assets/')) return LOCAL_ASSET_PATHS.has(value) ? value : null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' ? value : null
  } catch {
    return null
  }
}

interface Props {
  src: string | null | undefined
  alt: string
  kind: 'boss' | 'item'
  className?: string
}

const KIND_CLASSES = {
  boss: 'size-12 rounded-xl border border-[color-mix(in_srgb,var(--dash-accent)_20%,transparent)] bg-[color-mix(in_srgb,var(--dash-accent)_10%,transparent)]',
  item: 'size-9 rounded-[10px] border border-[var(--dash-subtle-border)] bg-[var(--dash-subtle)]',
}

export default function CatalogImage({ src, alt, kind, className }: Props) {
  const { theme } = useThemeStore()
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const imageSrc = catalogImageSrc(src)
  const classes = `${className ?? KIND_CLASSES[kind]} shrink-0`

  if (!imageSrc || failedSrc === imageSrc) {
    const icon = kind === 'boss' ? characterAssets[theme].imgSwords : characterAssets[theme].imgPkg
    return (
      <div className={`${classes} flex items-center justify-center`} role="img" aria-label={alt}>
        <img src={icon} alt="" className={kind === 'boss' ? 'size-[22px]' : 'size-[18px]'} />
      </div>
    )
  }

  return <img src={imageSrc} alt={alt} className={`${classes} object-contain`} onError={() => setFailedSrc(imageSrc)} />
}
