import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import "react-treeview/react-treeview.css"
import {filterComponentsByLabel, getCategoryComponentGroups} from "../../../common/ProcessDefinitionUtils"
import {getProcessCategory} from "../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import "../../../stylesheets/toolBox.styl"
import {ComponentGroup} from "../../../types"
import {ToolboxComponentGroup} from "./ToolboxComponentGroup"
import Tool from "./Tool"
import {useTranslation} from "react-i18next"

export default function ToolBox(props: { filter: string }): JSX.Element {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)
  const {t} = useTranslation()

  const componentGroups: ComponentGroup[] = useMemo(
    () => getCategoryComponentGroups(processDefinitionData, processCategory),
    [processDefinitionData, processCategory],
  )

  const filters = useMemo(
    () => props.filter?.toLowerCase().split(/\s/).filter(Boolean),
    [props.filter]
  )

  const groups = useMemo(
    () => componentGroups.map(filterComponentsByLabel(filters)).filter(g => g.components.length > 0),
    [componentGroups, filters]
  )

  return (
    <div id="toolbox">
      {(groups.length ?
        groups.map(componentGroup => (
          <ToolboxComponentGroup
            key={componentGroup.name}
            componentGroup={componentGroup}
            highlights={filters}
            flatten={groups.length === 1}
          />
        )) :
        (
          <Tool
            nodeModel={null}
            label={t("panels.creator.filter.noMatch", "no matching components")}
            disabled
          />
        ))}
    </div>
  )
}
