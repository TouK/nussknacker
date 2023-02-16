import {useParams} from "react-router"
import {ProcessesTabDataPath, ScenariosBasePath} from "./paths"
import React from "react"
import {CustomTabPage} from "./CustomTabPage"
import {useLocation} from "react-router-dom"
import {Navigate} from "react-router-dom-v5-compat"

function CustomTab(): JSX.Element {
  const {id, rest = ""} = useParams<{ id: string, rest: string }>()
  const {hash, search} = useLocation()

  const pass = `${rest}${hash}${search}`

  switch (id) {
    case "scenarios":
      // "Scenarios" is the main view and is defined in different way so we want to get rid of "customtabs" segment
      return <Navigate to={pass.length ? `${ScenariosBasePath}/${pass}` : ScenariosBasePath}/>
    case "legacy_scenarios":
      return <Navigate to={pass.length ? `${ProcessesTabDataPath}/${pass}` : ProcessesTabDataPath}/>
    default:
      return <CustomTabPage id={id}/>
  }
}

export default CustomTab
