import cn from "classnames"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useDispatch, useSelector} from "react-redux"
import TreeView from "react-treeview"
import {toggleToolboxGroup} from "../../../actions/nk/toolbars"
import {getOpenedNodeGroups} from "../../../reducers/selectors/toolbars"
import {NodesGroup} from "../../../types"
import Tool from "./Tool"

function nodeGroupIsEmpty(nodeGroup) {
  return nodeGroup.possibleNodes.length == 0
}

function useStateToggleWithReset(resetCondition: boolean, initialState = false): [boolean, () => void] {
  const [flag, setFlag] = useState(initialState)

  useEffect(
    () => {
      if (resetCondition) setFlag(initialState)
    },
    [resetCondition],
  )

  const toggle = useCallback(
    () => setFlag(state => !state),
    [],
  )

  return [flag, toggle]
}

export function ToolboxNodeGroup({nodeGroup, highlight}: {nodeGroup: NodesGroup, highlight?: string}) {
  const dispatch = useDispatch()
  const openedNodeGroups = useSelector(getOpenedNodeGroups)
  const {name} = nodeGroup

  const isEmpty = useMemo(() => nodeGroupIsEmpty(nodeGroup), [nodeGroup])

  const [forceCollapsed, toggleForceCollapsed] = useStateToggleWithReset(!highlight)

  const toggle = useCallback(() => {
    if (!isEmpty) {
      dispatch(toggleToolboxGroup(name))
    }
  }, [dispatch, isEmpty, name])

  const label = <span className={"group-label"} onClick={highlight ? toggleForceCollapsed : toggle}>{name}</span>

  return (
    <TreeView
      itemClassName={cn(isEmpty && "disabled")}
      nodeLabel={label}
      collapsed={isEmpty || (highlight ? forceCollapsed : !openedNodeGroups[name])}
      onClick={highlight ? toggleForceCollapsed : toggle}
    >
      {nodeGroup.possibleNodes.map(node => (
        <Tool
          nodeModel={node.node}
          label={node.label}
          key={node.type + node.label}
          highlight={highlight}
        />
      ))}
    </TreeView>
  )
}
