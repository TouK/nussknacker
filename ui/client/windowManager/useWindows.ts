import {useWindowManager, WindowId, WindowType} from "@touk/window-manager"
import {isEmpty} from "lodash"
import {useCallback} from "react"
import {useDispatch} from "react-redux"
import {displayModalEdgeDetails, displayModalNodeDetails, EventInfo, reportEvent} from "../actions/nk"
import {ConfirmDialogData} from "../components/modals/GenericConfirmDialog"
import {Edge, GroupNodeType, NodeType} from "../types"
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

  const editNode = useCallback(<N extends NodeType | GroupNodeType>(
    node: N,
    readonly?: boolean,
    eventInfo?: {name: string, category: string},
  ) => {
    // open<N>({
    //   title: node.id,
    //   kind: readonly ? WindowKind.viewNode : WindowKind.editNode,
    //   meta: node,
    // })

    dispatch(displayModalNodeDetails(node, readonly, eventInfo))
  }, [dispatch, open])

  const editEdge = useCallback(<E extends Edge>(edge: E) => {
    // open<E>({
    //   title: `${edge.from} -> ${edge.to}`,
    //   kind: WindowKind.editEdge,
    //   meta: edge,
    // })

    dispatch(displayModalEdgeDetails(edge))
  }, [dispatch, open])

  const confirm = useCallback((data: ConfirmDialogData, event?: EventInfo) => {
    if (!isEmpty(event)) {
      dispatch(reportEvent(event))
    }

    open<ConfirmDialogData>({
      title: data.text,
      kind: WindowKind.confirm,
      // TODO: get rid of meta
      meta: {confirmText: "Yes", denyText: "No", ...data},
    })
  }, [dispatch, open])

  return {open, confirm, editNode, editEdge}
}
