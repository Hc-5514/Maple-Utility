import { useQuery } from '@tanstack/react-query'
import client from '../../api/client'
import type { ApiResponse, Character } from '../../types'

interface Props {
  selectedId: number | null
  onChange: (id: number) => void
}

export default function CharacterSelector({ selectedId, onChange }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['characters'],
    queryFn: () =>
      client.get<ApiResponse<Character[]>>('/characters').then((r) => r.data.data),
  })

  if (isLoading) {
    return <div className="h-9 w-40 animate-pulse rounded bg-white/10" />
  }

  return (
    <select
      value={selectedId ?? ''}
      onChange={(e) => onChange(Number(e.target.value))}
      className="rounded border border-white/20 bg-[#2d2d44] px-3 py-1.5 text-sm text-white"
    >
      <option value="" disabled>캐릭터 선택</option>
      {data?.map((c) => (
        <option key={c.id} value={c.id}>
          {c.characterName}{c.worldName ? ` (${c.worldName})` : ''}
        </option>
      ))}
    </select>
  )
}
