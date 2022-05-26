import {Redirect, useRouteMatch} from "react-router"
import {ScenariosBasePath} from "./paths"
import React from "react"
import {CustomTabPage} from "./CustomTabPage"

function CustomTab(): JSX.Element {
  const {params} = useRouteMatch<{ id: string, rest: string }>()
  switch (params.id) {
    case "scenarios":
      // "Scenarios" is the main view and is defined in different way so we want to get rid of "customtabs" segment
      const basePath = ScenariosBasePath
      return <Redirect to={params.rest ? `${basePath}/${params.rest}` : basePath}/>
    default:
      return <CustomTabPage id={params.id}/>
  }
}

export default CustomTab
