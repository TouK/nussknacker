import {defaultsDeep} from "lodash"
import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {darkTheme} from "./darkTheme"
import {getTabs} from "../reducers/selectors/settings"
import {DynamicTab} from "./DynamicTab"
import {NotFound} from "./errors/NotFound"
import {NkThemeProvider} from "./theme"
import "../stylesheets/visualization.styl"
import {Page} from "./Page"
import {useParams} from "react-router-dom"

export function CustomTabPage<P extends Record<string, unknown>>({id, ...props}: { id?: string } & P): JSX.Element {
  const customTabs = useSelector(getTabs)
  const tab = useMemo(
    () => customTabs.find(tab => tab.id == id),
    [customTabs, id],
  )

  const {"*": rest} = useParams<{ "*": string }>()
  const basepath = window.location.pathname.replace(rest, "")
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
