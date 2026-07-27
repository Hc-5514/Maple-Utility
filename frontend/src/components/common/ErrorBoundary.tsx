import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-[#1a1a2e]">
          <p className="text-[#f87171]">오류가 발생했습니다.</p>
          <button
            onClick={() => window.location.reload()}
            className="rounded-lg bg-[#2d2d44] px-4 py-2 text-sm text-white/70 hover:text-white"
          >
            새로고침
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
