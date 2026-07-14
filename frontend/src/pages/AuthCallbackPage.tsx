export default function AuthCallbackPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#1a1a2e]">
      <div className="flex items-center gap-3 text-white/50">
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-[#4ade80] border-t-transparent" />
        <span>인증 처리 중...</span>
      </div>
    </div>
  )
}
