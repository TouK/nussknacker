import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {collapseGroup, editGroup, editNode, expandGroup} from "../../../actions/nk"
import {getProcessToDisplay} from "../../../reducers/selectors/graph"
import {getExpandedGroups} from "../../../reducers/selectors/groups"
import {GroupNodeType, NodeType} from "../../../types"
import {WindowContent,WindowKind} from "../../../windowManager"
import NodeUtils from "../NodeUtils"
import {ChildrenElement} from "./node/ChildrenElement"
import {ContentWrapper} from "./node/ContentWrapper"
import {getReadOnly} from "./node/selectors"
import {SubprocessContent} from "./node/SubprocessContent"

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

  const performNodeEdit = async () => {
    const action = NodeUtils.nodeIsGroup(editedNode) ?
      //TODO: try to get rid of this.state.editedNode, passing state of NodeDetailsContent via onChange is not nice...
      editGroup(processToDisplay, nodeToDisplay.id, editedNode) :
      editNode(processToDisplay, nodeToDisplay, editedNode)

    await dispatch(action)
    props.close()
  }

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

  return (
    <WindowContent {...props} buttons={buttons}>
      {/*<NodeDetailsModalHeader node={nodeToDisplay}/>*/}
      <ContentWrapper>
        <ChildrenElement
          editedNode={editedNode}
          readOnly={readOnly}
          currentNodeId={nodeToDisplay.id}
          updateNodeState={setEditedNode}
        >
          {NodeUtils.nodeIsSubprocess(nodeToDisplay) && (
            <SubprocessContent nodeToDisplay={nodeToDisplay} currentNodeId={nodeToDisplay.id}/>
          )}
        </ChildrenElement>
      </ContentWrapper>
    </WindowContent>
  )
}
