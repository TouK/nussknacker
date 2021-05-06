import {css} from "emotion"
import React, {PropsWithChildren, useLayoutEffect, useRef} from "react"
import {useDebouncedCallback} from "use-debounce"
import {useSize} from "../hooks/useSize"

type Props = {
  onChange: (freeSpace: number, currentHeight: number) => void,
}

export function FillCheck({children, onChange}: PropsWithChildren<Props>): JSX.Element {
  const wrapperRef = useRef()
  const childRef = useRef()
  const wrapper = useSize({ref: wrapperRef})
  const child = useSize({ref: childRef})

  const availableHeight = wrapper.height
  const currentHeight = child.height

  const [callback] = useDebouncedCallback<[freeSpace: number, currentHeight: number]>(
    (freeSpace, currentHeight) => onChange(freeSpace, currentHeight), 25,
  )

  useLayoutEffect(() => {
    if (availableHeight && currentHeight) {
      const freeSpace = availableHeight - currentHeight
      callback(freeSpace, currentHeight)
    }
  }, [callback, availableHeight, currentHeight])

  const wrapperStyles = css({
    overflowY: "hidden",
    display: "flex",
    flexDirection: "column",
    justifyContent: "stretch",
    flex: 1,
  })

  return (
    <div ref={wrapperRef} className={wrapperStyles}>
      <div ref={childRef}>{children}</div>
    </div>
  )
}
