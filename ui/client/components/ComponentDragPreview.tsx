import {useDragDropManager, useDragLayer} from "react-dnd"
import {css} from "emotion"
import {createPortal} from "react-dom"
import React, {forwardRef, useEffect, useMemo, useState} from "react"
import {ComponentPreview} from "./ComponentPreview"
import {useDebouncedValue} from "rooks"
import {NodeType} from "../types"

function useNotNull<T>(value: T) {
  const [current, setCurrent] = useState(() => value)
  useEffect(() => {
    if (!value) return
    setCurrent(value)
  }, [value])
  return current
}

export const ComponentDragPreview = forwardRef<HTMLDivElement>(function ComponentDragPreview({}, forwardedRef) {
  const manager = useDragDropManager()
  const monitor = manager.getMonitor()
  const {currentOffset, active, data} = useDragLayer(
    monitor => ({
      data: monitor.getItem(),
      active: monitor.isDragging(),
      currentOffset: monitor.getClientOffset(),
    })
  )

  const {x = 0, y = 0} = useNotNull(currentOffset) || {}
  const node = useNotNull<NodeType>(data)

  const targetIds = monitor.getTargetIds()
  const isOver = useMemo(() => {
    return targetIds.some(id => monitor.isOverTarget(id) && monitor.canDropOnTarget(id)) || monitor.didDrop()
  }, [monitor, targetIds])

  const [activeDelayed] = useDebouncedValue(active, 500)
  const wrapperStyles = css({
    display: active || activeDelayed ? "block" : "none",
    position: "fixed",
    top: 0,
    left: 0,
    zIndex: 100,
    pointerEvents: "none",
    userSelect: "none",
    transformOrigin: "top left",
    willChange: "transform",
  })

  return createPortal((
    <div
      ref={forwardedRef}
      className={wrapperStyles}
      style={{transform: `translate(${x}px, ${y}px)`}}
    >
      <ComponentPreview node={node} isActive={active} isOver={isOver}/>
    </div>
  ), document.body)
})
