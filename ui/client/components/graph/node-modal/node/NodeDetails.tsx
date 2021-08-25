import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {closeModals, collapseGroup, editGroup, editNode, expandGroup} from "../../../../actions/nk"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {getExpandedGroups} from "../../../../reducers/selectors/groups"
import {GroupNodeType, NodeType} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {ContentWrapper} from "./ContentWrapper"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"
import {SubprocessContent} from "./SubprocessContent"

export function NodeDetails(props: WindowContentProps<WindowKind, NodeType | GroupNodeType> & {readOnly?: boolean}): JSX.Element {
  const processToDisplay = useSelector(getProcessToDisplay)
  const readOnly = useSelector(getReadOnly)

  const {data: {meta: nodeToDisplay}} = props

  const [editedNode, setEditedNode] = useState(nodeToDisplay)

  useEffect(
    () => {
      setEditedNode(nodeToDisplay)
    },
    [nodeToDisplay],
  )

  const dispatch = useDispatch()

  useEffect(() => () => {
    dispatch(closeModals())
  }, [dispatch])

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

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
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
        title: expanded ? "Collapse" : "Expand",
        action: () => toggleGroup(),
      } :
      null
    ,
    [editedNode, expanded, toggleGroup],
  )

  const cancelButtonData = useMemo(
    () => ({title: t("dialog.button.cancel", "cancel"), action: () => props.close()}),
    [props, t],
  )

  const buttons: WindowButtonProps[] = useMemo(
    () => [groupToggleButtonData, cancelButtonData, applyButtonData].filter(Boolean),
    [applyButtonData, cancelButtonData, groupToggleButtonData],
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
    >
      <ContentWrapper>
        <NodeGroupContent
          editedNode={editedNode}
          readOnly={readOnly}
          currentNodeId={nodeToDisplay.id}
          updateNodeState={setEditedNode}
        >
          {NodeUtils.nodeIsSubprocess(nodeToDisplay) && (
            <SubprocessContent nodeToDisplay={nodeToDisplay} currentNodeId={nodeToDisplay.id}/>
          )}
        </NodeGroupContent>
      </ContentWrapper>
    </WindowContent>
  )
}
