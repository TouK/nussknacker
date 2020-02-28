import React, {PropsWithChildren, Children} from "react"
import Panel from "react-bootstrap/lib/Panel"
import styles from "./CollapsibleToolbar.styl"
import {useSelector, useDispatch} from "react-redux"
import {RootState} from "../../../reducers/index"
import {toggleToolbar} from "../../../actions/nk/toolbars"

export function CollapsibleToolbar({title, children, isHidden, id}: PropsWithChildren<{ id?: string, title: string, isHidden?: boolean }>) {
  const dispatch = useDispatch()

  if (isHidden || !Children.count(children)) {
    return null
  }

  const isCollapsed = useSelector<RootState, boolean>(s => id && s.toolbars.collapsed.includes(id))
  const onToggle = isExpanded => id && dispatch(toggleToolbar(id, !isExpanded))

  return (
    <>
      <div className={styles.panel}>
        <Panel expanded={!isCollapsed} onToggle={onToggle}>
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

