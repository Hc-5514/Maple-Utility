import { useThemeStore } from '../../stores/themeStore'
import { characterAssets } from '../character/assets'

interface Props {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  size?: 18 | 20
  disabled?: boolean
}

export default function ThemedCheckbox({ checked, onChange, label, size = 20, disabled = false }: Props) {
  const assets = characterAssets[useThemeStore().theme]
  return (
    <span className="relative inline-flex shrink-0" style={{ width: size, height: size }}>
      <input
        type="checkbox"
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="peer absolute inset-0 m-0 cursor-pointer opacity-0 disabled:cursor-not-allowed"
      />
      <span
        aria-hidden="true"
        className={`pointer-events-none flex size-full items-center justify-center rounded-[6px] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[var(--dash-accent)] ${checked ? 'bg-[var(--dash-accent)]' : 'border-[1.5px] border-[color-mix(in_srgb,var(--dash-muted)_80%,transparent)]'}`}
      >
        {checked && (
          <img src={size === 20 ? assets.imgCheckbox : assets.imgCheckOnAccent} alt="" className={size === 20 ? 'size-[14px]' : 'size-3'} />
        )}
      </span>
    </span>
  )
}
