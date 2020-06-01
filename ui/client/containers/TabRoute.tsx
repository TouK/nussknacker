import React, {ComponentType} from "react"
import {FadeRoute} from "./FadeRoute"
import {TabContent} from "./TabContent"

export function TabRoute({path, Component}: { path: string, Component: ComponentType }) {
  return (
    <FadeRoute path={path} exact>
      <TabContent>
        <Component/>
      </TabContent>
    </FadeRoute>
  )
}
