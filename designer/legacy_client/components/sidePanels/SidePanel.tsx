import React, {PropsWithChildren, useState} from "react"
import cn from "classnames"
import ErrorBoundary from "../common/ErrorBoundary"
import panelStyles from "./SidePanel.styl"
import {ScrollbarsExtended} from "./ScrollbarsExtended"
import TogglePanel from "../TogglePanel"
import {useDispatch, useSelector} from "react-redux"
import {isRightPanelOpened, isLeftPanelOpened} from "../../reducers/selectors/toolbars"
import {togglePanel} from "../../actions/nk/ui/layout"

export function useSidePanelToggle(side: "LEFT" | "RIGHT") {
  const dispatch = useDispatch()
  const isOpened = useSelector(side === "RIGHT" ? isRightPanelOpened : isLeftPanelOpened)
  const onToggle = () => dispatch(togglePanel(side))
  return {isOpened, onToggle}
}

type Props = {
  isCollapsed?: boolean,
  className?: string,
  innerClassName?: string,
  onScrollToggle?: (isEnabled: boolean) => void,
}

function ScrollTogglePanel(props: PropsWithChildren<Props>) {
  const {children, className, innerClassName, isCollapsed, onScrollToggle} = props
  return (
    <div className={cn(panelStyles.collapsible, className, !isCollapsed && panelStyles.isOpened)}>
      <ScrollbarsExtended onScrollToggle={onScrollToggle}>
        <ErrorBoundary>
          <div className={cn(panelStyles.sidePanel, innerClassName)}>
            {children}
          </div>
        </ErrorBoundary>
      </ScrollbarsExtended>
    </div>
  )
}

export enum PanelSide {
  Right = "RIGHT",
  Left = "LEFT",
}

type SidePanelProps = {
  side: PanelSide,
  className?: string,
}

export function SidePanel(props: PropsWithChildren<SidePanelProps>) {
  const {children, side, className} = props
  const {isOpened, onToggle} = useSidePanelToggle(side)
  const [showToggle, setShowToggle] = useState(true)

  return (
    <>
      {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle}/> : null}
      <ScrollTogglePanel
        onScrollToggle={setShowToggle}
        isCollapsed={!isOpened}
        className={cn(side, side === PanelSide.Left ? panelStyles.left : panelStyles.right)}
        innerClassName={className}
      >
        {children}
      </ScrollTogglePanel>
    </>
  )
}
