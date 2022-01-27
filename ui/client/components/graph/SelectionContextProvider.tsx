import _ from "lodash"
import React, {
  createContext,
  PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {useDebouncedCallback} from "use-debounce"
import {
  copySelection,
  cutSelection,
  deleteNodes,
  deleteSelection,
  nodesWithEdgesAdded,
  pasteSelection,
  selectAll,
} from "../../actions/nk"
import {error, success} from "../../actions/notificationActions"
import {redo, undo} from "../../actions/undoRedoActions"
import {events} from "../../analytics/TrackingEvents"
import * as ClipboardUtils from "../../common/ClipboardUtils"
import * as JsonUtils from "../../common/JsonUtils"
import {isInputEvent} from "../../containers/BindKeyboardShortcuts"
import {useDocumentListeners} from "../../containers/useDocumentListeners"
import {
  canModifySelectedNodes,
  getProcessCategory,
  getSelection,
  getSelectionState,
} from "../../reducers/selectors/graph"
import {getCapabilities} from "../../reducers/selectors/other"
import {getProcessDefinitionData} from "../../reducers/selectors/settings"
import {useGraph} from "./GraphContext"
import NodeUtils from "./NodeUtils"

const hasTextSelection = () => !!window.getSelection().toString()

type UserAction = ((e: Event) => unknown) | null

interface UserActions {
  copy: UserAction,
  paste: UserAction,
  canPaste: boolean,
  cut: UserAction,
  delete: UserAction,
  undo: UserAction,
  redo: UserAction,
  selectAll: UserAction,
}

function useClipboardParse() {
  const processCategory = useSelector(getProcessCategory)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  return useCallback(
    text => {
      const selection = JsonUtils.tryParseOrNull(text)
      const isValid = selection?.edges &&
        selection?.nodes?.every(node => NodeUtils.isNode(node) &&
          NodeUtils.isPlainNode(node) &&
          NodeUtils.isAvailable(node, processDefinitionData, processCategory))
      return isValid ? selection : null
    },
    [processCategory, processDefinitionData],
  )
}

function useClipboardPermission(): boolean | string {
  const clipboardPermission = useRef<PermissionStatus>()
  const [state, setState] = useState<"denied" | "granted" | "prompt">()
  const [text, setText] = useState("")
  const [content, setContent] = useState("")

  const parse = useClipboardParse()

  const checkClipboard = useCallback(async () => {
    try {
      setText(await navigator.clipboard.readText())
    } catch {
    }
  }, [])

  // if possible monitor clipboard for new content on each render
  if (state === "granted") {
    checkClipboard()
  }

  useEffect(() => {
    // parse clipboard content on change only
    setContent(parse(text))
  }, [parse, text])

  useEffect(() => {
    navigator.permissions?.query({name: "clipboard-read" as PermissionName}).then(permission => {
      clipboardPermission.current = permission
      setState(permission.state)
      permission.onchange = () => {
        setState(permission.state)
      }
    }).catch(() => {/*do nothing*/})
    return () => {
      if (clipboardPermission.current) {
        clipboardPermission.current.onchange = undefined
      }
    }
  }, [])

  return state === "prompt" || content
}

const SelectionContext = createContext<UserActions>(null)

export const useSelectionActions = (): UserActions => {
  const selectionActions = useContext(SelectionContext)
  if (!selectionActions) {
    throw new Error("used useSelectionActions outside provider")
  }
  return selectionActions
}

export default function SelectionContextProvider(props: PropsWithChildren<{ pastePosition: () => { x: number, y: number } }>): JSX.Element {
  const dispatch = useDispatch()
  const {t} = useTranslation()

  const selectionState = useSelector(getSelectionState)
  const capabilities = useSelector(getCapabilities)
  const selection = useSelector(getSelection)
  const canModifySelected = useSelector(canModifySelectedNodes)

  const [hasSelection, setHasSelection] = useState(hasTextSelection)

  const copy = useCallback(
    async (silent = false) => {
      if (silent && hasTextSelection()) {
        return
      }

      if (canModifySelected) {
        await ClipboardUtils.writeText(JSON.stringify(selection))
        const {nodes} = selection
        if (!silent) {
          dispatch(success(t("userActions.copy.success", {
            defaultValue: "Copied node",
            defaultValue_plural: "Copied {{count}} nodes",
            count: nodes.length,
          })))
        }
        return nodes
      } else {
        dispatch(error(t(
          "userActions.copy.failed",
          "Can not copy selected content. It should contain only plain nodes",
        )))
      }

    },
    [canModifySelected, dispatch, selection, t],
  )
  const cut = useCallback(
    async (isInternalEvent = false) => {
      const copied = await copy(true)
      if (copied) {
        const nodeIds = copied.map(node => node.id)
        dispatch(deleteNodes(nodeIds))
        if (!isInternalEvent) {
          dispatch(success(t("userActions.cut.success", {
            defaultValue: "Cut node",
            defaultValue_plural: "Cut {{count}} nodes",
            count: copied.length,
          })))
        }
      }
    },
    [copy, dispatch, t],
  )

  const parse = useClipboardParse()
  const graphGetter = useGraph()

  function calculatePastedNodePosition(node, pasteX, minNodeX, pasteY, minNodeY, random) {
    const currentNodePosition = node.additionalFields.layoutData
    const pasteNodePosition = {x: currentNodePosition.x + pasteX, y: currentNodePosition.y + pasteY }
    const selectionLayoutNodePosition = {x: pasteNodePosition.x - minNodeX, y: pasteNodePosition.y - minNodeY}
    const randomizedNodePosition = {x: selectionLayoutNodePosition.x + random, y: selectionLayoutNodePosition.y + random}
    return randomizedNodePosition
  }

  const [parseInsertNodes] = useDebouncedCallback((clipboardText) => {
    const selection = parse(clipboardText)
    if (selection) {
      const {x, y} = props.pastePosition()
      const minNodeX: number = _.min(selection.nodes.map((node) => node.additionalFields.layoutData.x))
      const minNodeY: number = _.min(selection.nodes.map((node) => node.additionalFields.layoutData.y))
      const random = (Math.floor(Math.random() * 20) + 1)
      const nodesWithPositions = selection.nodes.map((node, ix) => ({node, position: calculatePastedNodePosition(node, x, minNodeX, y, minNodeY, random)}))
      dispatch(nodesWithEdgesAdded(nodesWithPositions, selection.edges))
      dispatch(success(t("userActions.paste.success", {
        defaultValue: "Pasted node",
        defaultValue_plural: "Pasted {{count}} nodes",
        count: selection.nodes.length,
      })))
    } else {
      dispatch(error(t("userActions.paste.failed", "Cannot paste content from clipboard")))
    }
  }, 250)

  const paste = useCallback(
    async (event?: Event) => {
      if (isInputEvent(event)) {
        return
      }
      try {
        const clipboardText = await ClipboardUtils.readText(event)
        parseInsertNodes(clipboardText)
      } catch {
        dispatch(error(t("userActions.paste.notAvailable", "Paste button is not available. Try Ctrl+V")))
      }
    },
    [dispatch, parseInsertNodes, t],
  )

  const canAccessClipboard = useClipboardPermission()
  const userActions: UserActions = useMemo(() => ({
    copy: canModifySelected && !hasSelection && (() => dispatch(
      copySelection(
        copy,
        {category: events.categories.keyboard, action: events.actions.keyboard.copy},
      ),
    )),
    canPaste: !!canAccessClipboard,
    paste: capabilities.editFrontend && ((e) => dispatch(
      pasteSelection(
        () => paste(e),
        {category: events.categories.keyboard, action: events.actions.keyboard.paste},
      ),
    )),
    cut: canModifySelected && capabilities.editFrontend && (() => dispatch(
      cutSelection(
        cut,
        {category: events.categories.keyboard, action: events.actions.keyboard.cut},
      ),
    )),
    delete: canModifySelected && capabilities.editFrontend && (() => dispatch(
      deleteSelection(
        selectionState,
        {category: events.categories.keyboard, action: events.actions.keyboard.delete},
      ),
    )),
    undo: () => dispatch(
      undo({category: events.categories.keyboard, action: events.actions.keyboard.undo}),
    ),
    redo: () => dispatch(
      redo({category: events.categories.keyboard, action: events.actions.keyboard.redo}),
    ),
    selectAll: () => {
      dispatch(selectAll())
    },
  }), [
    copy, cut, paste, selectionState,
    hasSelection, canAccessClipboard, canModifySelected, capabilities.editFrontend, dispatch,
  ])

  useDocumentListeners(useMemo(() => ({
    selectionchange: () => setHasSelection(hasTextSelection),
  }), []))

  return (
    <SelectionContext.Provider value={userActions}>
      {props.children}
    </SelectionContext.Provider>
  )
}
