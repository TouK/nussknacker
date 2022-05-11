import cn from "classnames"
import React, {PropsWithChildren} from "react"
import "../stylesheets/visualization.styl"

export function Page({children, className}: PropsWithChildren<{className?: string}>) {
  return <div className={cn("Page", className)}>{children}</div>
}
