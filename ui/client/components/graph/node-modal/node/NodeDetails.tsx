import {css} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {SetStateAction, useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {editNode} from "../../../../actions/nk"
import {visualizationUrl} from "../../../../common/VisualizationUrl"
import {alpha, tint, useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {Edge, NodeType, Process} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {replaceWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"
import urljoin from "url-join"
import {BASE_PATH} from "../../../../config"
import {RootState} from "../../../../reducers"
import {getNodeId} from "../nodeUtils"

interface NodeDetailsProps extends WindowContentProps<WindowKind, { node: NodeType, process: Process }> {
  readOnly?: boolean,
}

export function NodeDetails(props: NodeDetailsProps): JSX.Element {
  const defaultProcess = useSelector(getProcessToDisplay)
  const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly))

  const {node, process = defaultProcess} = props.data.meta
  const currentNodeId = getNodeId(process, node)

  const [editedNode, setEditedNode] = useState(node)
  const [outputEdges, setOutputEdges] = useState(() => process.edges.filter(({from}) => from === currentNodeId))

  useEffect(() => {
    setEditedNode(node)
  }, [node])

  const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
    setEditedNode(node)
    setOutputEdges(edges)
  }, [])

  const dispatch = useDispatch()

  useEffect(() => {
    replaceWindowsQueryParams({nodeId: currentNodeId})
    return () => replaceWindowsQueryParams({}, {nodeId: currentNodeId})
  }, [currentNodeId])

  const performNodeEdit = useCallback(async () => {
    await dispatch(editNode(process, node, editedNode, outputEdges))
    props.close()
  }, [process, node, editedNode, outputEdges, dispatch, props])

  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
        disabled: !editedNode.id?.length,
        classname: css({
          //increase (x4) specificity over ladda
          "&&&&": {
            backgroundColor: theme.colors.accent,
            ":hover": {
              backgroundColor: tint(theme.colors.accent, .25),
            },
            "&[disabled], &[data-loading]": {
              "&, &:hover": {
                backgroundColor: alpha(theme.colors.accent, .5),
              },
            },
          },
        }),
      } :
      null,
    [editedNode.id?.length, performNodeEdit, readOnly, t, theme.colors.accent],
  )

  const openSubprocessButtonData: WindowButtonProps | null = useMemo(
    () => NodeUtils.nodeIsSubprocess(editedNode) ?
      {
        title: t("dialog.button.fragment.edit", "edit fragment"),
        action: () => {
          window.open(urljoin(BASE_PATH, visualizationUrl(editedNode.ref.id)))
        },
      } :
      null
    ,
    [editedNode, t],
  )

  const cancelButtonData = useMemo(
    () => ({title: t("dialog.button.cancel", "cancel"), action: () => props.close()}),
    [props, t],
  )

  const buttons: WindowButtonProps[] = useMemo(
    () => [openSubprocessButtonData, cancelButtonData, applyButtonData].filter(Boolean),
    [applyButtonData, cancelButtonData, openSubprocessButtonData],
  )

  const components = useMemo(() => {
    const HeaderTitle = () => <NodeDetailsModalHeader node={node}/>
    return {HeaderTitle}
  }, [node])

  return (
    <WindowContent
      {...props}
      buttons={buttons}
      components={components}
      classnames={{
        content: css({minHeight: "100%", display: "flex", ">div": {flex: 1}}),
      }}
    >
      <ErrorBoundary>
        <NodeGroupContent
          currentNodeId={currentNodeId}
          node={editedNode}
          edges={outputEdges}
          onChange={!readOnly && onChange}
        />
      </ErrorBoundary>
    </WindowContent>
  )
}

export default NodeDetails
