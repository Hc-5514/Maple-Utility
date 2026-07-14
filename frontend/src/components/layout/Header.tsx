import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../stores/authStore'

export default function Header() {
  const { user, hasApiKey, logout } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium transition-colors ${
      isActive ? 'text-[#4ade80]' : 'text-white/70 hover:text-white'
    }`

  return (
    <header className="sticky top-0 z-40 border-b border-white/10 bg-[#1a1a2e]">
      <div className="mx-auto flex h-14 max-w-screen-xl items-center justify-between px-4">
        <Link to="/" className="text-base font-bold text-white">
          🍁 메이플 유틸리티
        </Link>

        <nav className="flex items-center gap-5">
          {!user ? (
            <Link
              to="/login"
              className="rounded-md bg-[#4ade80] px-3 py-1.5 text-sm font-semibold text-black hover:bg-[#22c55e]"
            >
              로그인
            </Link>
          ) : (
            <>
              {hasApiKey && (
                <>
                  <NavLink to="/dashboard" className={navClass}>대시보드</NavLink>
                  <NavLink to="/hunting" className={navClass}>사냥기록</NavLink>
                  <NavLink to="/stats" className={navClass}>통계</NavLink>
                </>
              )}
              <NavLink to="/settings" className={navClass}>설정</NavLink>
              <span className="text-sm text-white/40">{user.nickname ?? user.email}</span>
              <button
                onClick={handleLogout}
                className="text-sm text-white/50 transition-colors hover:text-white"
              >
                로그아웃
              </button>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
