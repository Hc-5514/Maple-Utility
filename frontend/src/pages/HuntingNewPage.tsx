import { useNavigate } from 'react-router-dom'
import HuntingForm from '../components/hunting/HuntingForm'
import { useCreateHunting } from '../hooks/useHunting'

export default function HuntingNewPage() {
  const navigate = useNavigate()
  const createHunting = useCreateHunting()

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-white">사냥 기록 추가</h1>
      <HuntingForm
        submitLabel="등록"
        isSubmitting={createHunting.isPending}
        onSubmit={(data) =>
          createHunting.mutate(data, {
            onSuccess: () => navigate('/hunting'),
          })
        }
      />
    </div>
  )
}
