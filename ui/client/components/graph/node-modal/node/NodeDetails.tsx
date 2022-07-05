import {css} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {editNode} from "../../../../actions/nk"
import {visualizationUrl} from "../../../../common/VisualizationUrl"
import {alpha, tint, useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {NodeType, Process} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {replaceWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"
import urljoin from "url-join"
import {BASE_PATH} from "../../../../config"

export function NodeDetails(props: WindowContentProps<WindowKind, { node: NodeType, process: Process }> & { readOnly?: boolean }): JSX.Element {
  const process = useSelector(getProcessToDisplay)
  const readOnly = useSelector(s => getReadOnly(s, props.readOnly))

  const {data: {meta}} = props
  const {node: nodeToDisplay, process: processToDisplay = process} = meta
  const nodeId = processToDisplay.properties.isSubprocess ? nodeToDisplay.id.replace(`${processToDisplay.id}-`, "") : nodeToDisplay.id

  const [editedNode, setEditedNode] = useState(nodeToDisplay)
  const [outputEdges, setEditedOutputEdges] = useState(() => processToDisplay.edges.filter(({from}) => from === nodeId))

  useEffect(
    () => {
      setEditedNode(nodeToDisplay)
    },
    [nodeToDisplay],
  )

  const dispatch = useDispatch()

  useEffect(() => {
    replaceWindowsQueryParams({nodeId})
    return () => replaceWindowsQueryParams({}, {nodeId})
  }, [nodeId])

  const performNodeEdit = useCallback(async () => {
    //TODO: try to get rid of this.state.editedNode, passing state of NodeDetailsContent via onChange is not nice...
    await dispatch(editNode(processToDisplay, nodeToDisplay, editedNode, outputEdges))
    props.close()
  }, [processToDisplay, nodeToDisplay, editedNode, outputEdges, dispatch, props])

  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
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
    [performNodeEdit, readOnly, t, theme.colors.accent],
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
    const HeaderTitle = () => <NodeDetailsModalHeader node={nodeToDisplay}/>
    return {HeaderTitle}
  }, [nodeToDisplay])

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
          editedNode={editedNode}
          outputEdges={outputEdges}
          readOnly={readOnly}
          currentNodeId={nodeId}
          updateNodeState={setEditedNode}
          updateEdgesState={setEditedOutputEdges}
        />
      </ErrorBoundary>
    </WindowContent>
  )
}

export default NodeDetails
