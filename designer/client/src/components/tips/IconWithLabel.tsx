import {css} from "@emotion/css"
import React, {ComponentType, SVGProps} from "react"

export function IconWithLabel({icon: Icon, message}: {
  icon: ComponentType<SVGProps<SVGSVGElement>>,
  message: string,
}): JSX.Element {
  return (
    <div className={css({display: "flex", alignItems: "center"})}>
      <Icon className={css({width: "1em", height: "1em"})}/>
      <span className={css({marginLeft: ".5em"})}>{message}</span>
    </div>
  )
}
