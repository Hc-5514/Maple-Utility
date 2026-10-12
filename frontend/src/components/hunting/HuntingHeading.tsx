export default function HuntingHeading({ title }: { title: string }) {
  return (
    <div className="flex flex-col gap-[7px]">
      <p className="text-[11px] leading-[13px] font-semibold text-[var(--dash-accent)] [font-family:Inter,sans-serif]">
        MY MAPLE / HUNTING LOG
      </p>
      <h1 className="text-[34px] leading-[1.25] font-bold">{title}</h1>
      <p className="text-[14px] leading-[1.45] text-[var(--dash-muted)]">
        쌓이는 메소와 솔 에르다 조각, 오늘의 사냥을 기록하세요.
      </p>
    </div>
  )
}
