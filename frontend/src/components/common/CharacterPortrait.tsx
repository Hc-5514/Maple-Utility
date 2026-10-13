// Nexon look images are 300x300 transparent canvases; the character fills only the middle third,
// centered near (150, 165). Zoom into that area so the character fills the avatar box.
const ZOOM_PERCENT = 300
const CENTER_Y_PERCENT = 55

interface Props {
  src: string
  alt: string
  size: number
}

export default function CharacterPortrait({ src, alt, size }: Props) {
  return (
    <span className="relative block shrink-0 overflow-hidden" style={{ width: size, height: size }}>
      <img
        src={src}
        alt={alt}
        className="absolute max-w-none"
        style={{
          width: `${ZOOM_PERCENT}%`,
          left: '50%',
          top: '50%',
          transform: `translate(-50%, -${CENTER_Y_PERCENT}%)`,
        }}
      />
    </span>
  )
}
