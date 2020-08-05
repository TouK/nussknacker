import React, {ComponentType} from "react"
import {Route} from "react-router"
import {TabContent} from "./TabContent"

export function TabRoute({path, Component}: {path: string, Component: ComponentType}) {
  return (
    <Route path={path} exact>
      <TabContent>
        <Component/>
      </TabContent>
    </Route>
  )
}
