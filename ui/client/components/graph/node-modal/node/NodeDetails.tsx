import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import {css} from "emotion"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {collapseGroup, editGroup, editNode, expandGroup} from "../../../../actions/nk"
import {visualizationUrl} from "../../../../common/VisualizationUrl"
import {useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {getExpandedGroups} from "../../../../reducers/selectors/groups"
import {GroupNodeType, NodeType} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {replaceWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"

export function NodeDetails(props: WindowContentProps<WindowKind, NodeType | GroupNodeType> & {readOnly?: boolean}): JSX.Element {
  const processToDisplay = useSelector(getProcessToDisplay)
  const readOnly = useSelector(s => getReadOnly(s, props.readOnly))

  const {data: {meta: nodeToDisplay}} = props

  const [editedNode, setEditedNode] = useState(nodeToDisplay)

  useEffect(
    () => {
      setEditedNode(nodeToDisplay)
    },
    [nodeToDisplay],
  )

  const dispatch = useDispatch()

  useEffect(() => {
    const nodeId = nodeToDisplay.id
    replaceWindowsQueryParams({nodeId})
    return () => replaceWindowsQueryParams({}, {nodeId})
  }, [nodeToDisplay])

  const performNodeEdit = useCallback(async () => {
    const action = NodeUtils.nodeIsGroup(editedNode) ?
      //TODO: try to get rid of this.state.editedNode, passing state of NodeDetailsContent via onChange is not nice...
      editGroup(processToDisplay, nodeToDisplay.id, editedNode) :
      editNode(processToDisplay, nodeToDisplay, editedNode)

    await dispatch(action)
    props.close()
  }, [editedNode, processToDisplay, nodeToDisplay, dispatch, props])

  const expandedGroups = useSelector(getExpandedGroups)

  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
        classname: css({background: theme.colors.accent}),
      } :
      null,
    [performNodeEdit, readOnly, t],
  )

  const expanded = expandedGroups.includes(editedNode.id)

  const toggleGroup = useCallback(() => {
    dispatch(expanded ? collapseGroup(editedNode.id) : expandGroup(editedNode.id))
  }, [dispatch, editedNode, expanded])

  const groupToggleButtonData: WindowButtonProps | null = useMemo(
    () => NodeUtils.nodeIsGroup(editedNode) ?
      {
        title: expanded ? t("dialog.button.group.collapse", "collapse") : t("dialog.button.group.expand", "expand"),
        action: () => toggleGroup(),
      } :
      null
    ,
    [editedNode, expanded, t, toggleGroup],
  )

  const openSubprocessButtonData: WindowButtonProps | null = useMemo(
    () => NodeUtils.nodeIsSubprocess(editedNode) ?
      {
        title: t("dialog.button.fragment.edit", "edit fragment"),
        action: () => {
          window.open(visualizationUrl(editedNode.id))
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
    () => [openSubprocessButtonData, groupToggleButtonData, cancelButtonData, applyButtonData].filter(Boolean),
    [applyButtonData, cancelButtonData, groupToggleButtonData, openSubprocessButtonData],
  )

  const components = useMemo(() => {
    const HeaderTitle = () => <NodeDetailsModalHeader node={props.data.meta}/>
    return {HeaderTitle}
  }, [props.data.meta])

  return (
    <WindowContent
      {...props}
      buttons={buttons}
      components={components}
      classnames={{
        content: css({minHeight: "100%", display: "flex", ">div":{flex: 1}}),
      }}
    >
      <ErrorBoundary>
        <NodeGroupContent
          editedNode={editedNode}
          readOnly={readOnly}
          currentNodeId={nodeToDisplay.id}
          updateNodeState={setEditedNode}
        />
      </ErrorBoundary>
    </WindowContent>
  )
}
