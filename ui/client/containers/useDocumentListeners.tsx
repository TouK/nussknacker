import {useEffect} from "react"
import {useDispatch} from "react-redux"

type Listener<K extends keyof DocumentEventMap> = (event: DocumentEventMap[K]) => void
export type DocumentListeners = {
  [K in keyof DocumentEventMap]?: Listener<K> | false
}

export function useDocumentListeners(listeners: DocumentListeners): void {
  const dispatch = useDispatch()
  useEffect(
    () => {
      const entries = Object.entries(listeners).map(([type, listener]) => {
        if (listener) {
          document.addEventListener(type, listener)
          return () => document.removeEventListener(type, listener)
        }
      })
      return () => entries.forEach(unbind => unbind())
    },
    [listeners],
  )
}
