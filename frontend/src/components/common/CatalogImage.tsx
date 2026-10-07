import { useState } from 'react'
import { Package, Swords } from 'lucide-react'

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

export default function CatalogImage({ src, alt, kind, className = 'h-12 w-12' }: Props) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const imageSrc = catalogImageSrc(src)
  const classes = `${className} shrink-0 rounded bg-[#343448]`

  if (!imageSrc || failedSrc === imageSrc) {
    const Icon = kind === 'boss' ? Swords : Package
    return (
      <div className={`${classes} flex items-center justify-center text-white/50`} role="img" aria-label={alt}>
        <Icon size={20} aria-hidden="true" />
      </div>
    )
  }

  return <img src={imageSrc} alt={alt} className={`${classes} object-contain`} onError={() => setFailedSrc(imageSrc)} />
}
