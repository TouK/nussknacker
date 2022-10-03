import {flatMap, uniqBy} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {ToolbarsSide} from "../../reducers/toolbars"
import {Toolbar} from "../toolbarComponents/toolbar"
import {ToolbarsConfig} from "./types"
import {ToolbarSelector} from "./ToolbarSelector"
import {getToolbarsConfig} from "../../reducers/selectors/toolbars"
import {useUserSettings} from "../../common/userSettings"

const parseCollection = (collection: ToolbarsConfig): Toolbar[] => uniqBy<Toolbar>(
  flatMap(
    collection,
    (toolbars, defaultSide: ToolbarsSide) => toolbars.map(
      config => ({...config, defaultSide, component: <ToolbarSelector {...config}/>}),
    ),
  ),
  config => config.id,
)

export function useToolbarConfig(): [Toolbar[], string] {
  const {id, ...toolbarsCollection} = useSelector(getToolbarsConfig)
  const toolbars = useMemo(
    () => parseCollection(toolbarsCollection),
    [toolbarsCollection],
  )
  return [toolbars, id]
}
