import React, {PropsWithChildren, Children} from "react"
import Panel from "react-bootstrap/lib/Panel"
import styles from "./CollapsibleToolbar.styl"
import {useSelector, useDispatch} from "react-redux"
import {RootState} from "../../../reducers/index"
import {toggleToolbar} from "../../../actions/nk/toolbars"
import {useDragHandler} from "./DragHandle"

export function CollapsibleToolbar({title, children, isHidden, id}: PropsWithChildren<{ id?: string, title: string, isHidden?: boolean }>) {
  if (isHidden || !Children.count(children)) {
    return null
  }

  const dispatch = useDispatch()
  const isCollapsed = useSelector<RootState, boolean>(s => id && s.toolbars.collapsed.includes(id))
  const onToggle = isExpanded => id && dispatch(toggleToolbar(id, !isExpanded))

  const handlerProps = useDragHandler()

  return (
    <div className={styles.wrapper}>
      <Panel expanded={!isCollapsed} onToggle={onToggle} className={styles.panel}>
        <Panel.Heading {...handlerProps}>
          <Panel.Title toggle>
            {title}
          </Panel.Title>
        </Panel.Heading>
        <Panel.Collapse>
          <Panel.Body>
            {children}
          </Panel.Body>
        </Panel.Collapse>
      </Panel>
    </div>
  )
}
