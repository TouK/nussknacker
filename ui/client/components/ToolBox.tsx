import React, {useMemo} from "react"
import {useSelector, useDispatch} from "react-redux"

import TreeView from "react-treeview"
import * as ProcessDefitionUtils from "../common/ProcessDefinitionUtils"

import "react-treeview/react-treeview.css"
import "../stylesheets/toolBox.styl"

import Tool from "./Tool"
import {getProcessDefinitionData} from "./right-panel/selectors/settings"
import {getProcessCategory} from "./right-panel/selectors/graph"
import {RootState} from "../reducers/index"
import {toggleToolboxGroup} from "../actions/nk/toolbars"

export default function ToolBox() {
  const dispatch = useDispatch()
  const openedNodeGroups = useSelector<RootState>(s => s.toolbars.nodeToolbox.opened)
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
        {nodesToAdd.map((nodeGroup, i) => {
          const label =
            <span className={"group-label"} onClick={() => toggleGroup(nodeGroup)}>{nodeGroup.name}</span>
          return (
            <TreeView
              itemClassName={nodeGroupIsEmpty(nodeGroup) ? "disabled" : ""}
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
                  processDefinitionData={processDefinitionData}
                />
              ))}
            </TreeView>
          )
        })}
      </div>
    </div>
  )
}
