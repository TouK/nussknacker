import {uniqBy, flatMap} from "lodash"
import React, {useMemo} from "react"
import {ToolbarsSide} from "../../../reducers/toolbars"
import {Toolbar} from "../../toolbarComponents/toolbar"
import {defaultToolbarsConfig, ToolbarConfig, ToolbarsConfig} from "../defaultToolbarsConfig"
import {ToolbarSelector} from "../ToolbarSelector"

const parseCollection = (collection: ToolbarsConfig): Toolbar[] => uniqBy<Toolbar>(
  flatMap(
    collection,
    (toolbars, defaultSide: ToolbarsSide) => toolbars.map(
      config => ({...config, defaultSide, component: <ToolbarSelector {...config}/>}),
    ),
  ),
  config => config.id,
)

export function useToolbarDefualtSettings(): Toolbar[] {
  const toolbarsCollection = defaultToolbarsConfig
  return useMemo(
    () => parseCollection(toolbarsCollection),
    [toolbarsCollection],
  )
}
