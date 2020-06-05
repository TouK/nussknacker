import HealthCheck from "../components/HealthCheck"
import React, {PropsWithChildren} from "react"

export function Page({children}: PropsWithChildren<{}>) { return <div className="Page">{children}</div> }

export function PageWithHealthCheck({children}: PropsWithChildren<{}>) {
  return (
    <Page>
      <HealthCheck/>
      {children}
    </Page>
  )
}
