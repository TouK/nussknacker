import HealthCheck from "../components/HealthCheck"
import React, {PropsWithChildren} from "react"
import cn from "classnames"

export function Page({children, className}: PropsWithChildren<{className?: string}>) {
  return <div className={cn("Page", className)}>{children}</div>
}

export function PageWithHealthCheck({children, ...props}: PropsWithChildren<{className?: string}>) {
  return (
    <Page {...props}>
      <HealthCheck/>
      {children}
    </Page>
  )
}
