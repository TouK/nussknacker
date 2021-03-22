import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import "react-treeview/react-treeview.css"
import {filterNodesByLabel, getNodesToAddInCategory} from "../../../common/ProcessDefinitionUtils"
import {getProcessCategory} from "../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import "../../../stylesheets/toolBox.styl"
import {NodesGroup} from "../../../types"
import {ToolboxNodeGroup} from "./ToolboxNodeGroup"

export default function ToolBox(props: {filter: string}): JSX.Element {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)

  const nodesToAdd: NodesGroup[] = useMemo(
    () => getNodesToAddInCategory(processDefinitionData, processCategory),
    [processDefinitionData, processCategory],
  )

  return (
    <div id="toolbox">
      {nodesToAdd
        .map(filterNodesByLabel(props.filter))
        .map(nodeGroup => (
          <ToolboxNodeGroup
            key={nodeGroup.name}
            nodeGroup={nodeGroup}
            highlight={props.filter}
          />
        ))}
    </div>
  )
}
