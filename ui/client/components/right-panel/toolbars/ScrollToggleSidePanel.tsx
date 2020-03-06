import React, {PropsWithChildren, useState} from "react"
import cn from "classnames"
import styles2 from "../collipsableSidePanels.styl"
import {ScrollbarsExtended} from "../ScrollbarsExtended"
import ErrorBoundary from "react-error-boundary"
import styles from "./ToolbarsLayer.styl"
import TogglePanel from "../../TogglePanel"
import {useDispatch, useSelector} from "react-redux"
import {isRightPanelOpened, isLeftPanelOpened} from "../selectors/ui"
import {toggleRightPanel, toggleLeftPanel} from "../../../actions/nk/ui/layout"

export function useSidePanelToggle(side: "LEFT" | "RIGHT") {
  const dispatch = useDispatch()
  const isOpened = useSelector(side === "RIGHT" ? isRightPanelOpened : isLeftPanelOpened)
  const onToggle = () => dispatch(side === "RIGHT" ? toggleRightPanel() : toggleLeftPanel())
  return {isOpened, onToggle}
}

type Props = {
  isCollapsed?: boolean,
  isDragging?: boolean,
  className?: string,
  innerClassName?: string,
  onScrollToggle?: (isEnabled: boolean) => void,
}

function ScrollTogglePanel(props: PropsWithChildren<Props>) {
  const {children, className, innerClassName, isCollapsed, isDragging, onScrollToggle} = props
  return (
    <div className={cn(styles2.collapsible, className, !isCollapsed && styles2.isOpened)}>
      <ScrollbarsExtended onScrollToggle={onScrollToggle}>
        <ErrorBoundary>
          <div className={cn(styles.sidePanel, innerClassName, isDragging && styles.isDraggingStarted)}>
            {children}
          </div>
        </ErrorBoundary>
      </ScrollbarsExtended>
    </div>
  )
}

export function ScrollToggleSidePanel(props: PropsWithChildren<{ isDragging?: boolean, side: "LEFT" | "RIGHT" }>) {
  const {children, isDragging, side} = props
  const {isOpened, onToggle} = useSidePanelToggle(side)
  const [showToggle, setShowToggle] = useState(true)

  return (
    <>
      {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle}/> : null}
      <ScrollTogglePanel
        onScrollToggle={setShowToggle}
        isCollapsed={!isOpened}
        isDragging={isDragging}
        className={side === "LEFT" ? styles2.left : styles2.right}
        innerClassName={side === "LEFT" ? styles.left : styles.right}
      >
        {children}
      </ScrollTogglePanel>
    </>
  )
}
