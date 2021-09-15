import {useMemo} from "react"
import {useSelectionActions} from "../components/graph/SelectionContextProvider"
import {useDocumentListeners} from "./useDocumentListeners"

export const isInputEvent = (event: Event): boolean => ["INPUT", "SELECT", "TEXTAREA"].includes(event?.target["tagName"])
type KeyboradShortcutsMap = Record<string, (event: KeyboardEvent) => void>

export function BindKeyboardShortcuts({disabled}: { disabled?: boolean }): JSX.Element {
  const userActions = useSelectionActions()

  const keyHandlers: KeyboradShortcutsMap = useMemo(() => ({
    A: e => {
      if (e.ctrlKey || e.metaKey) {
        userActions.selectAll(e)
        e.preventDefault()
      }
    },
    Z: e => (e.ctrlKey || e.metaKey) && e.shiftKey ? userActions.redo(e) : userActions.undo(e),
    DELETE: userActions.delete,
    BACKSPACE: userActions.delete,
  }), [userActions])

  const eventHandlers = useMemo(() => ({
    keydown: event => isInputEvent(event) || keyHandlers[event.key.toUpperCase()]?.(event),
    copy: event => userActions.copy ? userActions.copy(event) : null,
    paste: event => userActions.paste ? userActions.paste(event) : null,
    cut: event => userActions.cut ? userActions.cut(event) : null,
  }), [keyHandlers, userActions])
  useDocumentListeners(!disabled && eventHandlers)

  return null
}
