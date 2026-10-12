import PageHeading from '../common/PageHeading'

export default function HuntingHeading({ title }: { title: string }) {
  return (
    <PageHeading
      eyebrow="MY MAPLE / HUNTING LOG"
      title={title}
      description="쌓이는 메소와 솔 에르다 조각, 오늘의 사냥을 기록하세요."
    />
  )
}
