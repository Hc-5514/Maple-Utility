import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import HuntingForm from '../components/hunting/HuntingForm'
import HuntingHeading from '../components/hunting/HuntingHeading'
import { useHuntingRecord, useUpdateHunting } from '../hooks/useHunting'

export default function HuntingEditPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const numId = Number(id)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { data: record, isLoading } = useHuntingRecord(numId)
  const updateHunting = useUpdateHunting()

  return (
    <div className="flex flex-col gap-7">
      <HuntingHeading title="사냥 기록 수정" />
      {isLoading ? (
        <p role="status" className="py-16 text-center text-[13px] text-[var(--dash-muted)]">
          불러오는 중...
        </p>
      ) : !record ? (
        <p role="alert" className="py-16 text-center text-[13px] text-[#f87171]">
          기록을 찾을 수 없습니다.
        </p>
      ) : (
        <>
          {submitError && (
            <p role="alert" className="-my-3 text-[13px] text-[#f87171]">
              {submitError}
            </p>
          )}
          <HuntingForm
            initialValues={record}
            submitLabel="수정 완료"
            isSubmitting={updateHunting.isPending}
            onSubmit={(data) => {
              setSubmitError(null)
              updateHunting.mutate(
                { id: numId, ...data },
                {
                  onSuccess: () => navigate('/hunting'),
                  onError: () => setSubmitError('수정 중 오류가 발생했습니다. 다시 시도해 주세요.'),
                },
              )
            }}
          />
        </>
      )}
    </div>
  )
}
