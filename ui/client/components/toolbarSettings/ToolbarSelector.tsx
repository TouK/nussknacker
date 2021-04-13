import React from "react"
import {ToolbarPanelProps} from "./DefaultToolbarPanel"
import {TOOLBAR_BUTTONS_MAP,ToolbarButtonTypes} from "./buttons"
import {TOOLBAR_COMPONENTS_MAP} from "./TOOLBAR_COMPONENTS_MAP"

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
