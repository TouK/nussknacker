import React, {PropsWithChildren, useLayoutEffect, useRef} from "react"
import {useSize} from "../hooks/useSize"

export function CountRowsToFill(props: PropsWithChildren<{items: number, onChange: (count: number) => void}>) {
  const wrapperRef = useRef()
  const childrenRef = useRef()
  const {height: availableHeight} = useSize({ref: wrapperRef})
  const {height: currentHeight} = useSize({ref: childrenRef})

  useLayoutEffect(() => {
    if (props.items && availableHeight && currentHeight) {
      const avgSize = currentHeight / props.items
      const bestMatch = Math.max(Math.floor(availableHeight / avgSize - 1), 1)
      if (bestMatch !== props.items) {
        props.onChange(Math.round((bestMatch + props.items) / 2))
      }
    }
  }, [availableHeight, currentHeight])

  return (
    <div
      ref={wrapperRef}
      style={{
        overflowY: "hidden",
        display: "flex",
        flexDirection: "column",
        justifyContent: "stretch",
        flex: 1,
      }}
    >
      <div ref={childrenRef}>{props.children}</div>
    </div>
  )
}
