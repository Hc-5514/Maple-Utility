import { Outlet } from 'react-router-dom'
import Header from './Header'

export default function Layout() {
  return (
    <div className="min-h-screen bg-[#1a1a2e]">
      <Header />
      <main className="mx-auto max-w-screen-xl px-4 py-6">
        {import.meta.env.VITE_USE_MOCK === 'true' && (
          <p className="mb-5 border-l-2 border-amber-400/70 pl-3 text-xs text-amber-100/80">
            2026-10-07 넥슨 응답 스냅샷 · 파티 인원·결정 수익·아이템 획득 이력은 미리보기용 모의값
          </p>
        )}
        <Outlet />
      </main>
    </div>
  )
}
