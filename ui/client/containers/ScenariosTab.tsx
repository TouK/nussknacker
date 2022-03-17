import {defaultsDeep} from "lodash"
import React, {useCallback, useMemo} from "react"
import {useRouteMatch} from "react-router"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {darkTheme} from "./darkTheme"
import {RemoteModuleTab} from "./DynamicTab"
import {NkThemeProvider} from "./theme"
import {useSelector} from "react-redux"
import {getTabs} from "../reducers/selectors/settings"

export const SCENARIOS_TAB_ID = "scenarios_2"

export function ScenariosTab(): JSX.Element {
  const {pathname} = window.location
  const customTabs = useSelector(getTabs)
  const tab = useMemo(
    () => customTabs.find(tab => tab.id === SCENARIOS_TAB_ID),
    [customTabs],
  )
  const {params} = useRouteMatch<{ rest: string }>()

  const addScenario = useAddProcessButtonProps()
  const addFragment = useAddProcessButtonProps(true)

  const componentProps = useMemo(
    () => ({
      basepath: pathname.replace(params.rest, ""),
      addScenario: addScenario.action,
      addFragment: addFragment.action,
    }),
    [addFragment, addScenario, params.rest, pathname]
  )

  const theme = useCallback(outerTheme => defaultsDeep(darkTheme, outerTheme), [])

  return (
    <NkThemeProvider theme={theme}>
      <div className="Page">
        <RemoteModuleTab
          url={tab.url}
          componentProps={componentProps}
        />
      </div>
    </NkThemeProvider>
  )
}

