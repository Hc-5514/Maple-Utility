import { useParams } from 'react-router-dom'

export default function HuntingEditPage() {
  const { id } = useParams()
  return (
    <div>
      <h1 className="text-2xl font-bold text-white">사냥 기록 수정 — #{id}</h1>
      <p className="mt-2 text-white/40">사냥 기록 수정 폼 (FE-09에서 구현)</p>
    </div>
  )
}
