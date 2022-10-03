import classNames from "classnames"
import React, {Children, PropsWithChildren, useCallback, useMemo, useState} from "react"
import Panel from "react-bootstrap/lib/Panel"
import {useDispatch, useSelector} from "react-redux"
import {toggleToolbar} from "../../actions/nk/toolbars"
import {ReactComponent as CollapseIcon} from "../../assets/img/arrows/panel-hide-arrow.svg"
import {getIsCollapsed, getToolbarsConfigId} from "../../reducers/selectors/toolbars"
import ErrorBoundary from "../common/ErrorBoundary"
import styles from "./CollapsibleToolbar.styl"
import {useDragHandler} from "./DragHandle"

export type CollapsibleToolbarProps = PropsWithChildren<{
  id?: string,
  title?: string,
  isHidden?: boolean,
}>

export function CollapsibleToolbar({title, children, isHidden, id}: CollapsibleToolbarProps): JSX.Element | null {
  const dispatch = useDispatch()
  const isCollapsed = useSelector(getIsCollapsed)
  const [isShort, setIsShort] = useState(isCollapsed(id))
  const [isCollapsing, setIsCollapsing] = useState(false)
  const [isExpanding, setIsExpanding] = useState(false)
  const configId = useSelector(getToolbarsConfigId)

  const onToggle = useCallback(
    () => id && dispatch(toggleToolbar(id, configId, !isCollapsed(id))),
    [configId, dispatch, id, isCollapsed],
  )
  const isCollapsible = !!id

  const {tabIndex, ...handlerProps} = useDragHandler()

  const collapseCallbacks = useMemo(() => ({
    onEnter: () => {
      setIsCollapsing(false)
      setIsExpanding(true)
      setIsShort(false)
    },
    onEntered: () => setIsExpanding(false),
    onExit: () => {
      setIsCollapsing(true)
      setIsExpanding(false)
      setIsShort(true)
    },
    onExited: () => setIsCollapsing(false),
  }), [])

  if (isHidden || !Children.count(children)) {
    return null
  }

  return (
    <div className={styles.wrapper}>
      <Panel
        expanded={!isCollapsed(id)}
        onToggle={onToggle}
        bsClass={styles.panel}
        className={classNames(
          isShort && styles.collapsed,
          isExpanding && styles.expanding,
          isCollapsing && styles.collapsing,
        )}
      >
        {title ?
          (
            <Panel.Heading {...handlerProps}>
              <Panel.Title toggle>
                <div className={styles.collapseTitle}>{title}</div>
                {isCollapsible && (
                  <CollapseIcon className={styles.collapseIcon}/>
                )}
              </Panel.Title>
            </Panel.Heading>
          ) :
          null}
        <Panel.Collapse {...collapseCallbacks}>
          <Panel.Body>
            <ErrorBoundary>
              {children}
            </ErrorBoundary>
          </Panel.Body>
        </Panel.Collapse>
      </Panel>
    </div>
  )
}
