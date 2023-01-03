import {defaultsDeep} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {useRouteMatch} from "react-router"
import {darkTheme} from "./darkTheme"
import {getTabs} from "../reducers/selectors/settings"
import {DynamicTab} from "./DynamicTab"
import {NotFound} from "./errors/NotFound"
import {NkThemeProvider} from "./theme"
import "../stylesheets/visualization.styl"
import {Page} from "./Page"

export function CustomTabPage<P extends Record<string, unknown>>({id, ...props}: { id?: string } & P): JSX.Element {
  const customTabs = useSelector(getTabs)
  const tab = useMemo(
    () => customTabs.find(tab => tab.id == id),
    [customTabs, id],
  )

  const {params} = useRouteMatch<{ rest: string }>()
  const basepath = window.location.pathname.replace(params.rest, "")
  return tab ?
    (
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <Page>
          <DynamicTab tab={tab} componentProps={{...props, basepath}}/>
        </Page>
      </NkThemeProvider>
    ) :
    (
      <NotFound/>
    )

}
