import {flatMap, uniqBy} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {ToolbarsSide} from "../../reducers/toolbars"
import {Toolbar} from "../toolbarComponents/toolbar"
import {ToolbarsConfig} from "./defaultToolbarsConfig"
import {ToolbarSelector} from "./ToolbarSelector"
import {getToolbarsConfig} from "./selectors/toolbarsConfig"

const parseCollection = (collection: ToolbarsConfig): Toolbar[] => uniqBy<Toolbar>(
  flatMap(
    collection,
    (toolbars, defaultSide: ToolbarsSide) => toolbars.map(
      config => ({...config, defaultSide, component: <ToolbarSelector {...config}/>}),
    ),
  ),
  config => config.id,
)

export function useToolbarConfig(): Toolbar[] {
  const toolbarsCollection = useSelector(getToolbarsConfig)
  return useMemo(
    () => parseCollection(toolbarsCollection),
    [toolbarsCollection],
  )
}
