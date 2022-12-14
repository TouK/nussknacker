import React, {Children, PropsWithChildren, useCallback, useMemo, useState} from "react"
import Panel from "react-bootstrap/lib/Panel"
import {useDispatch, useSelector} from "react-redux"
import {toggleToolbar} from "../../actions/nk/toolbars"
import {ReactComponent as CollapseIcon} from "../../assets/img/arrows/panel-hide-arrow.svg"
import {getIsCollapsed, getToolbarsConfigId} from "../../reducers/selectors/toolbars"
import ErrorBoundary from "../common/ErrorBoundary"
import styleVariables from "../../stylesheets/_variables.styl"
import {useDragHandler} from "./DragHandle"
import {css} from "@emotion/css"

const {
  panelHeaderBackground,
  panelBackground,
  panelHeaderText,
  panelHeaderTextSize,
  panelBorder,
  panelText,
  sidebarWidth,
} = styleVariables

export type CollapsibleToolbarProps = PropsWithChildren<{
  id?: string,
  title?: string,
  isHidden?: boolean,
}>

export function CollapsibleToolbar({title, children, isHidden, id}: CollapsibleToolbarProps): JSX.Element | null {
  const dispatch = useDispatch()
  const isCollapsed = useSelector(getIsCollapsed)
  const [isShort, setIsShort] = useState(isCollapsed(id))
  const configId = useSelector(getToolbarsConfigId)

  const onToggle = useCallback(
    () => id && dispatch(toggleToolbar(id, configId, !isCollapsed(id))),
    [configId, dispatch, id, isCollapsed],
  )
  const isCollapsible = !!id

  const {tabIndex, ...handlerProps} = useDragHandler()

  const collapseCallbacks = useMemo(() => ({
    onEnter: () => setIsShort(false),
    onExit: () => setIsShort(true),
  }), [])

  if (isHidden || !Children.count(children)) {
    return null
  }

  return (
    <div className={css({
      pointerEvents: "auto",
    })}
    >
      <Panel
        expanded={!isCollapsed(id)}
        onToggle={onToggle}
        bsClass={css({
          background: panelBackground,
          border: `0 solid ${panelBorder}`,
          minWidth: sidebarWidth,
          maxWidth: sidebarWidth,

          "&-heading": {
            background: panelHeaderBackground,
            color: panelHeaderText,
            textTransform: "uppercase",
          },

          "&-title": {
            fontSize: panelHeaderTextSize,
            fontFamily: "Open Sans",
            fontWeight: 600,
            color: panelHeaderText,

            "& > a": {
              height: "2em",
              display: "flex",
              justifyContent: "space-between",
              lineHeight: "2em",
              flexGrow: 0,
            },

            "& > a, & > a:focus, & > a:hover": {
              textDecoration: "none",
              color: panelHeaderText,
            },
          },

          "&-body": {
            color: panelText,
            userSelect: "text",
          },
        })}
        className={css({
          opacity: isShort ? .86 : 1,
          transition: `all ${isShort ? .2 : .3}s ease-in-out`,
        })}
      >
        {title ?
          (
            <Panel.Heading {...handlerProps}>
              <Panel.Title toggle>
                <div className={css({
                  padding: "0 .75em",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  flex: 1,
                })}
                >{title}</div>
                {isCollapsible && (
                  <CollapseIcon className={css({
                    padding: ".5em .8em",
                    height: "2em",
                    flexShrink: 0,
                    transition: "all .3s",
                    transform: `rotate(${isShort ? 180 : 90}deg)`,
                  })}
                  />
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
