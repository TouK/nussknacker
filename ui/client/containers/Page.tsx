import cn from "classnames"
import React, {PropsWithChildren} from "react"

export function Page({children, className}: PropsWithChildren<{className?: string}>) {
  return <div className={cn("Page", className)}>{children}</div>
}
