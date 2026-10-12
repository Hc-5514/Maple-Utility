import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import HuntingForm from '../components/hunting/HuntingForm'
import HuntingHeading from '../components/hunting/HuntingHeading'
import { useCreateHunting } from '../hooks/useHunting'

export default function HuntingNewPage() {
  const navigate = useNavigate()
  const createHunting = useCreateHunting()
  const [submitError, setSubmitError] = useState<string | null>(null)

  return (
    <div className="flex flex-col gap-7">
      <HuntingHeading title="사냥 기록 추가" />
      {submitError && (
        <p role="alert" className="-my-3 text-[13px] text-[#f87171]">
          {submitError}
        </p>
      )}
      <HuntingForm
        submitLabel="등록"
        isSubmitting={createHunting.isPending}
        onSubmit={(data) => {
          setSubmitError(null)
          createHunting.mutate(data, {
            onSuccess: () => navigate('/hunting'),
            onError: () => setSubmitError('저장 중 오류가 발생했습니다. 다시 시도해 주세요.'),
          })
        }}
      />
    </div>
  )
}
