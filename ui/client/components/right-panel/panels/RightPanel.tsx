import React, {PropsWithChildren, Children} from "react"
import {Panel} from "react-bootstrap"

export function RightPanel({title, children, isHidden}: PropsWithChildren<{ title: string, isHidden?: boolean }>) {
  if (isHidden || !Children.count(children)) {
    return null
  }

  return (
    <Panel defaultExpanded>
      <Panel.Heading><Panel.Title toggle>{title}</Panel.Title></Panel.Heading>
      <Panel.Collapse>
        <Panel.Body>
          {children}
        </Panel.Body>
      </Panel.Collapse>
    </Panel>
  )
}
