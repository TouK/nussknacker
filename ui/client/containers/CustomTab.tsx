import {defaultsDeep} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {useRouteMatch} from "react-router"
import {darkTheme} from "./darkTheme"
import {getTabs} from "../reducers/selectors/settings"
import {DynamicTab} from "./DynamicTab"
import NotFound from "./errors/NotFound"
import {NkThemeProvider} from "./theme"
import "../stylesheets/visualization.styl"

export function CustomTab<P extends Record<string, unknown>>({id, ...props}: { id?: string } & P): JSX.Element {
  const customTabs = useSelector(getTabs)
  const {pathname} = window.location

  const {params} = useRouteMatch<{id: string, rest: string}>()
  const tab = useMemo(
    () => customTabs.find(tab => tab.id == (params.id || id)),
    [customTabs, id, params.id],
  )

  return tab ?
    (
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <div className="Page">
          <DynamicTab tab={tab} componentProps={{...props, basepath: pathname.replace(params.rest, "")}}/>
        </div>
      </NkThemeProvider>
    ) :
    (
      <NotFound/>
    )
}

export default CustomTab
