import {useWindowManager, WindowId, WindowType} from "@touk/window-manager"
import {useCallback} from "react"
import {useDispatch} from "react-redux"
import {reportEvent} from "../actions/nk"
import {WindowKind} from "./WindowKind"

export function useWindows(parent?: WindowId) {
  const wm = useWindowManager(parent)
  const dispatch = useDispatch()

  const open = useCallback(<M extends any = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
    const {id, title} = wm.open(windowData)
    dispatch(reportEvent({
      category: "window_manager",
      action: "window_open",
      name: `${title} (${id})`,
    }))
  }, [dispatch, wm])

  return {open}
}
