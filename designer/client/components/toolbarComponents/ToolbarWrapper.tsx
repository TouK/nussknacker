import React, {Children, PropsWithChildren, useCallback, useMemo, useState} from "react"
import Panel from "react-bootstrap/lib/Panel"
import {useDispatch, useSelector} from "react-redux"
import {toggleToolbar} from "../../actions/nk/toolbars"
import {ReactComponent as CollapseIcon} from "../../assets/img/arrows/panel-hide-arrow.svg"
import {ReactComponent as CloseIcon} from "../../assets/img/close.svg"
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

export type ToolbarWrapperProps = PropsWithChildren<{
  id?: string,
  title?: string,
  noTitle?: boolean,
  onClose?: () => void,
}>

const Title = styled.div({
  padding: "0 .25em",
  overflow: "hidden",
  textOverflow: "ellipsis",
  flex: 1,
})

const IconWrapper = styled.div({
  padding: 0,
  flexShrink: 0,
  border: 0,
  background: "none",
  display: "flex",
  alignItems: "center",
})

const StyledCollapseIcon = styled(CollapseIcon, {
  shouldForwardProp: (name) => name !== "collapsed",
})(({collapsed}: { collapsed?: boolean }) => ({
  padding: "0 .25em",
  height: "1em",
  transition: "all .3s",
  transform: `rotate(${collapsed ? 180 : 90}deg)`,
}))

const StyledCloseIcon = styled(CloseIcon)({
  height: "1em",
  width: "1em",
})

const StyledPanel = styled(Panel)(({expanded}) => ({
  opacity: expanded ? 1 : .86,
  transition: `all ${expanded ? .3 : .2}s ease-in-out`,
}))

const bsClass = css({
  pointerEvents: "auto",
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
      display: "flow-root",
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

const Line = styled.div({
  display: "flex",
  height: "2em",
  justifyContent: "space-between",
  lineHeight: "2em",
  padding: "0 .5em",
  flexGrow: 0,
})

export function ToolbarWrapper(props: ToolbarWrapperProps): JSX.Element | null {
  const {title, noTitle, children, id, onClose} = props
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

  const handlerProps = useDragHandler()

  if (!Children.count(children)) {
    return null
  }

  const isCollapsible = !!id && !!title

  return (
    <StyledPanel
      expanded={!isCollapsed(id)}
      onToggle={onToggle}
      bsClass={bsClass}
    >
      {!noTitle && (
        <Panel.Heading {...handlerProps} tabIndex={-1}>
          <Panel.Title toggle={isCollapsible}>
            <Line>
              <Title>{title}</Title>
              {isCollapsible && (
                <IconWrapper>
                  <StyledCollapseIcon collapsed={collapsed}/>
                </IconWrapper>
              )}
              {onClose && (
                <IconWrapper as="button" onClick={onClose}>
                  <StyledCloseIcon/>
                </IconWrapper>
              )}
            </Line>
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
  )
}

