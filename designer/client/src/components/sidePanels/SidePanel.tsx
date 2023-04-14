import React, {forwardRef, PropsWithChildren, useEffect, useRef, useState} from "react"
import cn from "classnames"
import ErrorBoundary from "../common/ErrorBoundary"
import panelStyles from "./SidePanel.styl"
import {ScrollbarsExtended} from "./ScrollbarsExtended"
import TogglePanel from "../TogglePanel"
import {useDispatch, useSelector} from "react-redux"
import {isLeftPanelOpened, isRightPanelOpened} from "../../reducers/selectors/toolbars"
import {togglePanel} from "../../actions/nk/ui/layout"
import {useGraph} from "../graph/GraphContext"
import {Graph} from "../graph/Graph"

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

const ScrollTogglePanel = forwardRef<HTMLDivElement, PropsWithChildren<Props>>(function ScrollTogglePanel(props, ref) {
  const {children, className, innerClassName, isCollapsed, onScrollToggle} = props
  return (
    <div ref={ref} className={cn(panelStyles.collapsible, className, !isCollapsed && panelStyles.isOpened)}>
      <ScrollbarsExtended onScrollToggle={onScrollToggle}>
        <ErrorBoundary>
          <div className={cn(panelStyles.sidePanel, innerClassName)}>
            {children}
          </div>
        </ErrorBoundary>
      </ScrollbarsExtended>
    </div>
  )
})

export enum PanelSide {
  Right = "RIGHT",
  Left = "LEFT",
}

type SidePanelProps = {
  side: PanelSide,
  className?: string,
}

// adjust viewport for PanZoomPlugin.panToCells
function useGraphViewportAdjustment(side: keyof Graph["viewportAdjustment"], isOccupied: boolean) {
  const ref = useRef<HTMLDivElement>()
  const getGraph = useGraph()
  useEffect(() => {
    getGraph().adjustViewport({
      [side]: isOccupied ? ref.current.getBoundingClientRect().width : 0,
    })
  }, [getGraph, isOccupied, side])
  return ref
}

export function SidePanel(props: PropsWithChildren<SidePanelProps>) {
  const {children, side, className} = props
  const {isOpened, onToggle} = useSidePanelToggle(side)
  const [showToggle, setShowToggle] = useState(true)

  const ref = useGraphViewportAdjustment(
    side === PanelSide.Left ? "left" : "right",
    isOpened && showToggle
  )

  return (
    <>
      {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle}/> : null}
      <ScrollTogglePanel
        ref={ref}
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
