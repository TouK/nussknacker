import cn from "classnames"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useDispatch, useSelector} from "react-redux"
import TreeView from "react-treeview"
import {toggleToolboxGroup} from "../../../actions/nk/toolbars"
import {getOpenedComponentGroups, getToolbarsConfigId} from "../../../reducers/selectors/toolbars"
import {ComponentGroup} from "../../../types"
import Tool from "./Tool"

function isEmptyComponentGroup(componentGroup: ComponentGroup) {
  return componentGroup.components.length == 0
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

export function ToolboxComponentGroup({componentGroup, highlight}: {componentGroup: ComponentGroup, highlight?: string}) {
  const dispatch = useDispatch()
  const openedComponentGroups = useSelector(getOpenedComponentGroups)
  const {name} = componentGroup

  const isEmpty = useMemo(() => isEmptyComponentGroup(componentGroup), [componentGroup])

  const [forceCollapsed, toggleForceCollapsed] = useStateToggleWithReset(!highlight)
  const configId = useSelector(getToolbarsConfigId)

  const toggle = useCallback(() => {
    if (!isEmpty) {
      dispatch(toggleToolboxGroup(name, configId))
    }
  }, [dispatch, configId, isEmpty, name])

  const label = <span className={"group-label"} onClick={highlight ? toggleForceCollapsed : toggle}>{name}</span>

  return (
    <TreeView
      itemClassName={cn(isEmpty && "disabled")}
      nodeLabel={label}
      collapsed={isEmpty || (highlight ? forceCollapsed : !openedComponentGroups[name])}
      onClick={highlight ? toggleForceCollapsed : toggle}
    >
      {componentGroup.components.map(component => (
        <Tool
          nodeModel={component.node}
          label={component.label}
          key={component.type + component.label}
          highlight={highlight}
        />
      ))}
    </TreeView>
  )
}
