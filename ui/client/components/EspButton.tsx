import cn from "classnames"
import React from "react"
import styles from "../stylesheets/graph.styl"
import {ButtonWithFocus} from "./withFocus"

export function EspButton({className, ...props}: React.DetailedHTMLProps<React.ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>) {
  return (
    <ButtonWithFocus {...props} className={cn(styles.espButton, className)}/>
  )
}
