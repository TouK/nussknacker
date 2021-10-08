import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import "react-treeview/react-treeview.css"
import {filterComponentsByLabel, getCategoryComponentGroups} from "../../../common/ProcessDefinitionUtils"
import {getProcessCategory} from "../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import "../../../stylesheets/toolBox.styl"
import {ComponentGroup} from "../../../types"
import {ToolboxComponentGroup} from "./ToolboxComponentGroup"

export default function ToolBox(props: {filter: string}): JSX.Element {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)

  const componentGroups: ComponentGroup[] = useMemo(
    () => getCategoryComponentGroups(processDefinitionData, processCategory),
    [processDefinitionData, processCategory],
  )

  return (
    <div id="toolbox">
      {componentGroups
        .map(filterComponentsByLabel(props.filter))
        .map(componentGroup => (
          <ToolboxComponentGroup
            key={componentGroup.name}
            componentGroup={componentGroup}
            highlight={props.filter}
          />
        ))}
    </div>
  )
}
