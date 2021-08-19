import {useWindowManager, WindowId, WindowType} from "@touk/window-manager"
import {useCallback} from "react"
import {useDispatch} from "react-redux"
import {displayModalEdgeDetails, displayModalNodeDetails, reportEvent} from "../actions/nk"
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

  return {open, editNode, editEdge}
}
