import {css} from "emotion"
import React from "react"
import HeaderIcon from "./HeaderIcon"

export function IconWithLabel({icon, message}: { icon: string, message: string }): JSX.Element {
  return (
    <div className={css({display: "flex", alignItems: "center"})}>
      <HeaderIcon
        className={css({width: "1em", height: "1em"})}
        icon={icon}
      />
      <span className={css({marginLeft: ".5em"})}>{message}</span>
    </div>
  )
}
