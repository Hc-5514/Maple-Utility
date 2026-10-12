import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../stores/authStore'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from '../dashboard/assets'
import { useUnsavedChanges } from '../../router/UnsavedChangesGuard'
import { settingsAssets } from '../settings/assets'
import { useLocation } from 'react-router-dom'
export default function Header() {
  const { user, hasApiKey, logout } = useAuthStore()
  const { theme, setTheme } = useThemeStore()
  const assets = dashboardAssets[theme]
  const navigate = useNavigate()
  const guard = useUnsavedChanges()
  const settings = useLocation().pathname === '/settings'
  const themeAssets = settingsAssets[theme]
  const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex h-10 items-center rounded-[10px] px-4 text-[14px] leading-[17px] transition-colors ${isActive ? 'bg-[var(--dash-accent)]/9 text-[var(--dash-accent)]' : 'text-[var(--dash-muted)] hover:text-[var(--dash-text)]'}`
  return (
    <header
      data-theme={theme}
      className="dashboard-theme sticky top-0 z-40 border-b border-[var(--dash-border)] bg-[var(--dash-card)] text-[var(--dash-text)] backdrop-blur-[16px]"
    >
      <div className="relative flex min-h-[87px] flex-wrap items-center justify-between gap-3 px-4 py-3 lg:px-12 lg:py-0">
        <Link
          to="/"
          className="text-[23px] leading-[28px] font-bold whitespace-nowrap lg:w-[182px] lg:shrink-0"
        >
          🍁 메이플 유틸리티
        </Link>
        <nav
          data-full-nav={Boolean(user && hasApiKey)}
          className="app-navigation order-3 flex w-full flex-wrap items-center gap-2 lg:order-none lg:w-auto"
        >
          {!user ? (
            <Link to="/login" className={navClass({ isActive: false })}>
              로그인
            </Link>
          ) : (
            <>
              {hasApiKey && (
                <>
                  <NavLink to="/dashboard" className={navClass}>
                    대시보드
                  </NavLink>
                  <NavLink to="/hunting" className={navClass}>
                    사냥기록
                  </NavLink>
                  <NavLink to="/stats" className={navClass}>
                    통계
                  </NavLink>
                </>
              )}
              <NavLink to="/settings" className={navClass}>
                설정
              </NavLink>
              <button
                type="button"
                onClick={() => {
                  guard.requestLeave(() => {
                    logout()
                    navigate('/login')
                  })
                }}
                className="min-h-10 text-[14px] leading-5 text-[var(--dash-muted)]"
              >
                로그아웃
              </button>
            </>
          )}
        </nav>
        <div className="flex h-[38px] w-[72px] shrink-0 items-center gap-1 rounded-full border border-[var(--dash-border)] bg-[var(--dash-track)] p-[3px]">
          <button
            type="button"
            aria-label="라이트 모드"
            aria-pressed={theme === 'light'}
            onClick={() => setTheme('light')}
            className={`flex size-[30px] items-center justify-center rounded-full ${theme === 'light' ? 'bg-[var(--dash-accent)]/19' : ''}`}
          >
            <img src={settings ? themeAssets.imgSun : assets.imgControlIcon} alt="" />
          </button>
          <button
            type="button"
            aria-label="다크 모드"
            aria-pressed={theme === 'dark'}
            onClick={() => setTheme('dark')}
            className={`flex size-[30px] items-center justify-center rounded-full ${theme === 'dark' ? 'bg-[var(--dash-accent)]/19' : ''}`}
          >
            <img src={settings ? themeAssets.imgMoon : assets.imgControlIcon1} alt="" />
          </button>
        </div>
      </div>
    </header>
  )
}
