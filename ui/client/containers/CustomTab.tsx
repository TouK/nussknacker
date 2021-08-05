import {defaultsDeep} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {useRouteMatch} from "react-router"
import {getCustomTabs} from "../reducers/selectors/settings"
import {darkTheme} from "./darkTheme"
import {DynamicTab} from "./DynamicTab"
import NotFound from "./errors/NotFound"
import {NkThemeProvider} from "./theme"

export function CustomTab(): JSX.Element {
  const customTabs = useSelector(getCustomTabs)
  const {params} = useRouteMatch<{id: string}>()
  const tab = useMemo(
    () => customTabs.find(tab => tab.id == params.id),
    [customTabs, params],
  )

  return tab ?
    (
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <div className="Page">
          <DynamicTab tab={tab}/>
        </div>
      </NkThemeProvider>
    ) :
    (
      <NotFound/>
    )
}

export const CustomTabPath = "/customtabs"
