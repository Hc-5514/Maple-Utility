import { Outlet, useLocation } from 'react-router-dom'
import Header from './Header'
import { useThemeStore } from '../../stores/themeStore'
import { dashboardAssets } from '../dashboard/assets'
export default function Layout() {
  const { pathname } = useLocation()
  const dashboard = pathname === '/dashboard'
  const themed = dashboard || pathname === '/hunting'
  const { theme } = useThemeStore()
  const assets = dashboardAssets[theme]
  return (
    <div
      data-theme={theme}
      className={
        themed
          ? 'dashboard-theme relative isolate min-h-screen overflow-clip bg-[linear-gradient(90deg,var(--dash-bg-start),var(--dash-bg-end))] text-[var(--dash-text)]'
          : 'min-h-screen bg-[#1a1a2e]'
      }
    >
      {themed && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
        >
          <img
            src={assets.imgAmbientViolet}
            alt=""
            className="absolute top-[-20px] left-[-190px]"
          />
          <img src={assets.imgAmbientCyan} alt="" className="absolute top-[330px] left-[780px]" />
          {dashboard && (
            <img
              src={assets.imgAmbientViolet1}
              alt=""
              className="absolute top-[1060px] left-[310px]"
            />
          )}
        </div>
      )}
      <Header />
      <main
        className={
          themed
            ? 'relative px-4 pt-10 pb-12 sm:px-6 lg:px-12'
            : 'mx-auto max-w-screen-xl px-4 py-6'
        }
      >
        {import.meta.env.VITE_USE_MOCK === 'true' && !themed && (
          <p className="mb-5 border-l-2 border-amber-400/70 pl-3 text-xs text-amber-100/80">
            2026-10-07 넥슨 응답 스냅샷 · 파티 인원·결정 수익·아이템 획득 이력은 미리보기용 모의값
          </p>
        )}
        <Outlet />
      </main>
    </div>
  )
}
