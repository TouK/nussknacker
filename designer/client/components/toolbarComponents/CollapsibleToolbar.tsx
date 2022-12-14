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

const wrapper = styles.wrapper
const panel = styles.panel
const collapsed = styles.collapsed
const expanding = styles.expanding
const collapsing = styles.collapsing
const collapseTitle = styles.collapseTitle
const collapseIcon = styles.collapseIcon

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
    <div className={wrapper}>
      <Panel
        expanded={!isCollapsed(id)}
        onToggle={onToggle}
        bsClass={panel}
        className={classNames(
          isShort && collapsed,
          isExpanding && expanding,
          isCollapsing && collapsing,
        )}
      >
        {title ?
          (
            <Panel.Heading {...handlerProps}>
              <Panel.Title toggle>
                <div className={collapseTitle}>{title}</div>
                {isCollapsible && (
                  <CollapseIcon className={collapseIcon}/>
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
