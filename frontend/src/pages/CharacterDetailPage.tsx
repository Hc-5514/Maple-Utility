import { useParams } from 'react-router-dom'

export default function CharacterDetailPage() {
  const { characterId } = useParams()
  return (
    <div>
      <h1 className="text-2xl font-bold text-white">캐릭터 상세 — #{characterId}</h1>
      <p className="mt-2 text-white/40">캐릭터별 컨텐츠 상세 (FE-07에서 구현)</p>
    </div>
  )
}
