import React, {Children, PropsWithChildren, useCallback, useMemo, useState} from "react"
import Panel from "react-bootstrap/lib/Panel"
import {useDispatch, useSelector} from "react-redux"
import {toggleToolbar} from "../../actions/nk/toolbars"
import {ReactComponent as CollapseIcon} from "../../assets/img/arrows/panel-hide-arrow.svg"
import {getIsCollapsed, getToolbarsConfigId} from "../../reducers/selectors/toolbars"
import ErrorBoundary from "../common/ErrorBoundary"
import styleVariables from "../../stylesheets/_variables.styl"
import {useDragHandler} from "./DragHandle"
import styled from "@emotion/styled"
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
}>

const ResetPointerEvents = styled.div({pointerEvents: "auto"})

const Title = styled.div({
  padding: "0 .75em",
  overflow: "hidden",
  textOverflow: "ellipsis",
  flex: 1,
})

const Icon = styled(CollapseIcon, {
  shouldForwardProp: (name) => name !== "collapsed",
})(({collapsed}: { collapsed: boolean }) => ({
  padding: ".5em .8em",
  height: "2em",
  flexShrink: 0,
  transition: "all .3s",
  transform: `rotate(${collapsed ? 180 : 90}deg)`,
}))

const StyledPanel = styled(Panel)(({expanded}) => ({
  opacity: expanded ? 1 : .86,
  transition: `all ${expanded ? .3 : .2}s ease-in-out`,
}))

const bsClass = css({
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
      overflow: "hidden",
    },

    "& > a, & > a:focus, & > a:hover": {
      textDecoration: "none",
      color: panelHeaderText,
    },
  },

  "&-body": {
    color: panelText,
    userSelect: "text",
    display: "flow-root",
  },
})

export function CollapsibleToolbar({title, children, id}: CollapsibleToolbarProps): JSX.Element | null {
  const dispatch = useDispatch()
  const isCollapsed = useSelector(getIsCollapsed)
  const [collapsed, setCollapsed] = useState(isCollapsed(id))
  const configId = useSelector(getToolbarsConfigId)

  const onToggle = useCallback(
    () => id && dispatch(toggleToolbar(id, configId, !isCollapsed(id))),
    [configId, dispatch, id, isCollapsed],
  )

  const collapseCallbacks = useMemo(() => ({
    onEnter: () => setCollapsed(false),
    onExit: () => setCollapsed(true),
  }), [])

  const hasTitle = !!title
  const isCollapsible = !!id && hasTitle

  const handlerProps = useDragHandler()

  if (!Children.count(children)) {
    return null
  }

  return (
    <ResetPointerEvents>
      <StyledPanel
        expanded={!isCollapsed(id)}
        onToggle={onToggle}
        bsClass={bsClass}
      >
        {hasTitle && (
          <Panel.Heading {...handlerProps} tabIndex={-1}>
            <Panel.Title toggle>
              <Title>{title}</Title>
              {isCollapsible && <Icon collapsed={collapsed}/>}
            </Panel.Title>
          </Panel.Heading>
        )}
        <Panel.Collapse {...collapseCallbacks}>
          <Panel.Body>
            <ErrorBoundary>
              {children}
            </ErrorBoundary>
          </Panel.Body>
        </Panel.Collapse>
      </StyledPanel>
    </ResetPointerEvents>
  )
}

