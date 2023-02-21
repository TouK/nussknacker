import {ProcessesTabDataPath, ScenariosBasePath} from "./paths"
import React from "react"
import {CustomTabPage} from "./CustomTabPage"
import {Navigate, useLocation, useParams} from "react-router-dom"

function CustomTab(): JSX.Element {
  const {id, "*": rest = ""} = useParams<{ id: string, "*": string }>()
  const {hash, search} = useLocation()

  const pass = `${rest}${hash}${search}`

  switch (id) {
    case "scenarios":
      // "Scenarios" is the main view and is defined in different way so we want to get rid of "customtabs" segment
      return <Navigate to={pass.length ? `${ScenariosBasePath}/${pass}` : ScenariosBasePath} replace/>
    case "legacy_scenarios":
      return <Navigate to={pass.length ? `${ProcessesTabDataPath}/${pass}` : ProcessesTabDataPath} replace/>
    default:
      return <CustomTabPage id={id}/>
  }
}

export default CustomTab
