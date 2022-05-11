import {useWindowManager, WindowId, WindowType} from "@touk/window-manager"
import {defaults, isEmpty, mapValues, uniq, without} from "lodash"
import * as queryString from "query-string"
import {useCallback, useMemo} from "react"
import {useDispatch} from "react-redux"
import {EventInfo, reportEvent} from "../actions/nk"
import {ensureArray} from "../common/arrayUtils"

import {isEdgeEditable} from "../common/EdgeUtils"
import {useUserSettings} from "../common/userSettings"
import {defaultArrayFormat, setAndPreserveLocationParams} from "../common/VisualizationUrl"
import {ConfirmDialogData} from "../components/modals/GenericConfirmDialog"
import history from "../history"
import {Edge, NodeType} from "../types"
import {WindowKind} from "./WindowKind"

export function parseWindowsQueryParams<P extends Record<string, string | string[]>>(append: P, remove?: P): Record<string, string[]> {
  const query = queryString.parse(window.location.search, {arrayFormat: defaultArrayFormat})
  const keys = uniq(Object.keys({...append, ...remove}))
  return Object.fromEntries(keys.map(key => {
    const current = ensureArray(query[key]).map(decodeURIComponent)
    const withAdded = uniq(current.concat(append?.[key]))
    const cleaned = without(withAdded, ...ensureArray(remove?.[key])).filter(Boolean)
    return [key, cleaned]
  }))
}

export function replaceWindowsQueryParams<P extends Record<string, string | string[]>>(add: P, remove?: P): void {
  const params = parseWindowsQueryParams(add, remove)
  const search = setAndPreserveLocationParams(mapValues(params, v => ensureArray(v).map(encodeURIComponent)))
  history.replace({search})
}

export function useWindows(parent?: WindowId) {
  const {open: _open, closeAll} = useWindowManager(parent)
  const dispatch = useDispatch()
  const [settings] = useUserSettings()
  const forceDisableModals = useMemo(() => settings["wm.forceDisableModals"], [settings])

  const open = useCallback(async <M extends any = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
    const isModal = windowData.isModal === undefined ?
      !forceDisableModals :
      windowData.isModal && !forceDisableModals
    const {id, title} = await _open({isResizable: false, ...windowData, isModal})
    dispatch(reportEvent({
      category: "window_manager",
      action: "window_open",
      name: `${title} (${id})`,
    }))
  }, [dispatch, forceDisableModals, _open])

  const openNodeWindow = useCallback((
    node: NodeType,
    readonly?: boolean,
  ) => {
    open({
      title: node.id,
      isResizable: true,
      kind: readonly ? WindowKind.viewNode : WindowKind.editNode,
      meta: node,
    })

  }, [open])

  const editEdge = useCallback((edge: Edge) => {
    if (isEdgeEditable(edge)) {
      open({
        title: `${edge.from} -> ${edge.to}`,
        isResizable: true,
        kind: WindowKind.editEdge,
        meta: edge,
      })
    }
  }, [open])

  const confirm = useCallback((data: ConfirmDialogData, event?: EventInfo) => {
    if (!isEmpty(event)) {
      dispatch(reportEvent(event))
    }

    open({
      title: data.text,
      kind: WindowKind.confirm,
      meta: defaults(data, {confirmText: "Yes", denyText: "No"}),
    })
  }, [dispatch, open])

  return useMemo(() => ({
    open,
    confirm,
    openNodeWindow,
    editEdge,
    close: closeAll,
  }), [confirm, editEdge, open, openNodeWindow, closeAll])
}
