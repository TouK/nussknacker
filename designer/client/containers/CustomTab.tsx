import {Redirect, useRouteMatch} from "react-router"
import {ProcessesTabDataPath, ScenariosBasePath} from "./paths"
import React from "react"
import {CustomTabPage} from "./CustomTabPage"

function CustomTab(): JSX.Element {
  const {params} = useRouteMatch<{ id: string, rest: string }>()
  switch (params.id) {
    case "scenarios":
      // "Scenarios" is the main view and is defined in different way so we want to get rid of "customtabs" segment
      return <Redirect to={params.rest ? `${ScenariosBasePath}/${params.rest}` : ScenariosBasePath}/>
    case "legacy_scenarios":
      return <Redirect to={params.rest ? `${ProcessesTabDataPath}/${params.rest}` : ProcessesTabDataPath}/>
    default:
      return <CustomTabPage id={params.id}/>
  }
}

export default CustomTab
