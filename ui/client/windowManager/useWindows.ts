import {useWindowManager, WindowId, WindowType} from "@touk/window-manager"
import {defaults, isEmpty} from "lodash"
import {useCallback} from "react"
import {useDispatch} from "react-redux"
import {EventInfo, reportEvent} from "../actions/nk"

import {isEdgeEditable} from "../common/EdgeUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import NodeUtils from "../components/graph/NodeUtils"
import {ConfirmDialogData} from "../components/modals/GenericConfirmDialog"
import history from "../history"
import {Edge, GroupNodeType, NodeType} from "../types"
import {WindowKind} from "./WindowKind"

function setQueryParams(params: Partial<Record<"edgeId" | "nodeId", string | string[]>>) {
  history.replace({
    pathname: history.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams(params),
  })
}

export function useWindows(parent?: WindowId) {
  const wm = useWindowManager(parent)
  const dispatch = useDispatch()

  const open = useCallback(<M extends any = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
    const {id, title} = wm.open({isResizable: false, ...windowData})
    dispatch(reportEvent({
      category: "window_manager",
      action: "window_open",
      name: `${title} (${id})`,
    }))
  }, [dispatch, wm])

  const openNodeWindow = useCallback((
    node: NodeType | GroupNodeType,
    readonly?: boolean,
  ) => {
    setQueryParams({nodeId: node.id, edgeId: null})

    open({
      title: node.id,
      isResizable: true,
      isModal: !readonly,
      kind: readonly ? WindowKind.viewNode : WindowKind.editNode,
      meta: node,
    })

  }, [open])

  const editEdge = useCallback((edge: Edge) => {
    if (isEdgeEditable(edge)) {
      setQueryParams({nodeId: null, edgeId: NodeUtils.edgeId(edge)})

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

  return {open, confirm, openNodeWindow, editEdge}
}
