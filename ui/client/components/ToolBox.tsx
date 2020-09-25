import cn from "classnames"
import React, {useMemo} from "react"
import {useDispatch, useSelector} from "react-redux"

import TreeView from "react-treeview"

import "react-treeview/react-treeview.css"
import {toggleToolboxGroup} from "../actions/nk/toolbars"
import * as ProcessDefitionUtils from "../common/ProcessDefinitionUtils"
import {getProcessCategory} from "../reducers/selectors/graph"
import {getProcessDefinitionData} from "../reducers/selectors/settings"
import {getOpenedNodeGroups} from "../reducers/selectors/toolbars"
import "../stylesheets/toolBox.styl"

import Tool from "./Tool"

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
              {nodeGroup.possibleNodes.map(node => (
                <Tool
                  nodeModel={node.node}
                  label={node.label}
                  key={node.type + node.label}
                />
              ))}
            </TreeView>
          )
        })}
      </div>
    </div>
  )
}
