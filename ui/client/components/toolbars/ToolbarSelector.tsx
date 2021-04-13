import React from "react"
import {ToolbarPanelProps} from "./DefaultToolbarPanel"
import {TOOLBAR_BUTTONS_MAP} from "./toolbarSettings/TOOLBAR_BUTTONS_MAP"
import {TOOLBAR_COMPONENTS_MAP} from "./toolbarSettings/TOOLBAR_COMPONENTS_MAP"
import {ToolbarButtonTypes} from "./toolbarSettings/ToolbarSettingsTypes"

export const ToolbarSelector = ({buttons, ...props}: Omit<ToolbarPanelProps, "children"> & {buttons?: ToolbarButtonTypes[]}): JSX.Element => {
  const Component = TOOLBAR_COMPONENTS_MAP[props.id] || TOOLBAR_COMPONENTS_MAP.DefaultPanel
  return (
    <Component {...props}>
      {buttons?.map(type => <ToolbarButtonSelector key={type} type={type}/>)}
    </Component>
  )
}

const ToolbarButtonSelector = ({type}: {type: ToolbarButtonTypes}): JSX.Element => {
  const Component = TOOLBAR_BUTTONS_MAP[type]
  return <Component/>
}
