import { useNavigate, useParams } from 'react-router-dom'
import HuntingForm from '../components/hunting/HuntingForm'
import { useHuntingRecord, useUpdateHunting } from '../hooks/useHunting'

export default function HuntingEditPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const numId = Number(id)

  const { data: record, isLoading } = useHuntingRecord(numId)
  const updateHunting = useUpdateHunting()

  if (isLoading) {
    return (
      <div className="space-y-5">
        <div className="h-8 w-48 animate-pulse rounded bg-white/10" />
        <div className="h-64 animate-pulse rounded-xl bg-white/10" />
      </div>
    )
  }

  if (!record) {
    return <p className="text-sm text-[#f87171]">기록을 찾을 수 없습니다.</p>
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-white">사냥 기록 수정</h1>
      <HuntingForm
        initialValues={record}
        submitLabel="수정 완료"
        isSubmitting={updateHunting.isPending}
        onSubmit={(data) =>
          updateHunting.mutate(
            { id: numId, ...data },
            { onSuccess: () => navigate('/hunting') },
          )
        }
      />
    </div>
  )
}
