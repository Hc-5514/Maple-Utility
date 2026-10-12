import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useBeforeUnload, useBlocker, useLocation } from 'react-router-dom'
import ThemedConfirmDialog from '../components/common/ThemedConfirmDialog'

const Context = createContext({
  setDirty: (() => {}) as (dirty: boolean) => void,
  requestLeave: (action: () => void) => action(),
})
export function useUnsavedChanges() {
  return useContext(Context)
}
export function UnsavedChangesProvider({ children }: { children: ReactNode }) {
  const [dirty, setDirty] = useState(false)
  const [action, setAction] = useState<(() => void) | null>(null)
  const bypass = useRef(false)
  const location = useLocation()
  useEffect(() => {
    bypass.current = false
  }, [location.key])
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && !bypass.current && currentLocation.pathname !== nextLocation.pathname
  )
  useBeforeUnload(
    useCallback(
      (event: BeforeUnloadEvent) => {
        if (dirty) {
          event.preventDefault()
          event.returnValue = ''
        }
      },
      [dirty]
    )
  )
  const requestLeave = useCallback(
    (next: () => void) => {
      if (dirty) setAction(() => next)
      else next()
    },
    [dirty]
  )
  const cancel = useCallback(() => {
    setAction(null)
    if (blocker.state === 'blocked') blocker.reset()
  }, [blocker])
  const confirm = () => {
    bypass.current = true
    setDirty(false)
    if (action) {
      const next = action
      setAction(null)
      next()
    } else if (blocker.state === 'blocked') blocker.proceed()
  }
  return (
    <Context.Provider value={{ setDirty, requestLeave }}>
      {children}
      {(action || blocker.state === 'blocked') && (
        <ThemedConfirmDialog title="저장하지 않은 변경사항" onCancel={cancel} onConfirm={confirm}>
          저장하지 않은 즐겨찾기와 표시 순서 변경사항이 사라집니다. 이동하시겠습니까?
        </ThemedConfirmDialog>
      )}
    </Context.Provider>
  )
}
