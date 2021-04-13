import React from "react"
import {TOOLBAR_BUTTONS_MAP, ToolbarButton} from "./buttons"
import {ToolbarConfig} from "./defaultToolbarsConfig"
import {TOOLBAR_COMPONENTS_MAP} from "./TOOLBAR_COMPONENTS_MAP"

function buttonSelector(btn: ToolbarButton, i: number) {
  const Component = TOOLBAR_BUTTONS_MAP[btn.type]
  return <Component key={i} {...btn}/>
}

export const ToolbarSelector = ({buttons, ...props}: ToolbarConfig): JSX.Element => {
  const Component = TOOLBAR_COMPONENTS_MAP[props.id] || TOOLBAR_COMPONENTS_MAP.DefaultPanel
  return (
    <Component {...props}>
      {buttons?.map(buttonSelector)}
    </Component>
  )
}
