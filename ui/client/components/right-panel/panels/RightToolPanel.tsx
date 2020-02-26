import React, {PropsWithChildren, Children} from "react"
import Panel from "react-bootstrap/lib/Panel"
import styles from "./RightToolPanel.styl"

export function RightToolPanel({title, children, isHidden}: PropsWithChildren<{ title: string, isHidden?: boolean }>) {
  if (isHidden || !Children.count(children)) {
    return null
  }

  return (
    <>
      <div className={styles.panel}>
        <Panel defaultExpanded>
          <Panel.Heading><Panel.Title toggle>{title}</Panel.Title></Panel.Heading>
          <Panel.Collapse>
            <Panel.Body>
              {children}
            </Panel.Body>
          </Panel.Collapse>
        </Panel>
      </div>
    </>
  )
}

