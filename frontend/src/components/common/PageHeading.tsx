export default function PageHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string
  title: string
  description: string
}) {
  return (
    <div className="flex flex-col gap-[7px]">
      <p className="text-[11px] leading-[13px] font-semibold text-[var(--dash-accent)] [font-family:Inter,sans-serif]">
        {eyebrow}
      </p>
      <h1 className="text-[34px] leading-[1.25] font-bold">{title}</h1>
      <p className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">{description}</p>
    </div>
  )
}
