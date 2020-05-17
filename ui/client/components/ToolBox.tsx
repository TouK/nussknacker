import React, {useMemo} from "react"
import {useSelector, useDispatch} from "react-redux"

import TreeView from "react-treeview"
import * as ProcessDefitionUtils from "../common/ProcessDefinitionUtils"

import "react-treeview/react-treeview.css"
import "../stylesheets/toolBox.styl"

import Tool, {useToolIcon} from "./Tool"
import {getProcessDefinitionData} from "../reducers/selectors/settings"
import {getProcessCategory} from "../reducers/selectors/graph"
import {toggleToolboxGroup} from "../actions/nk/toolbars"
import {getOpenedNodeGroups} from "../reducers/selectors/toolbars"
import cn from "classnames"

export default function ToolBox() {
  const dispatch = useDispatch()
  const openedNodeGroups = useSelector(getOpenedNodeGroups)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)

  const nodesToAdd = useMemo(
    () => ProcessDefitionUtils.getNodesToAddInCategory(processDefinitionData, processCategory),
    [processDefinitionData, processCategory],
  )

  const nodeGroupIsEmpty = (nodeGroup) => {
    return nodeGroup.possibleNodes.length == 0
  }

  const toggleGroup = (nodeGroup) => {
    if (!nodeGroupIsEmpty(nodeGroup)) {
      dispatch(toggleToolboxGroup(nodeGroup.name))
    }
  }

  return (
    <div id="toolbox">
      <div>
        {nodesToAdd.map(nodeGroup => {
          const label = <span className={"group-label"} onClick={() => toggleGroup(nodeGroup)}>{nodeGroup.name}</span>
          return (
            <TreeView
              itemClassName={cn(nodeGroupIsEmpty(nodeGroup) && "disabled")}
              key={nodeGroup.name}
              nodeLabel={label}
              collapsed={!openedNodeGroups[nodeGroup.name]}
              onClick={() => toggleGroup(nodeGroup)}
            >
              {nodeGroup.possibleNodes.map(node => {
                const icon = useToolIcon(node)
                return (
                  <Tool
                    nodeModel={node.node}
                    label={node.label}
                    key={node.type + node.label}
                    icon={icon}
                  />
                )
              })}
            </TreeView>
          )
        })}
      </div>
    </div>
  )
}
