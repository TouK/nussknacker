import {defaultsDeep} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {useRouteMatch, useLocation} from "react-router"
import {darkTheme} from "./darkTheme"
import {getTabs} from "../reducers/selectors/settings"
import {DynamicTab} from "./DynamicTab"
import NotFound from "./errors/NotFound"
import {NkThemeProvider} from "./theme"

export function CustomTab(): JSX.Element {
  const customTabs = useSelector(getTabs)
  const {pathname} = useLocation()

  const {params} = useRouteMatch<{id: string, rest: string}>()
  const tab = useMemo(
    () => customTabs.find(tab => tab.id == params.id),
    [customTabs, params],
  )

  return tab ?
    (
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <div className="Page">
          <DynamicTab tab={tab} basepath={pathname.replace(params.rest, "")}/>
        </div>
      </NkThemeProvider>
    ) :
    (
      <NotFound/>
    )
}

export const CustomTabPath = "/customtabs"
