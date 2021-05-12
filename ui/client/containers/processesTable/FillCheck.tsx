import {css} from "emotion"
import React, {PropsWithChildren, useLayoutEffect} from "react"
import {useDebouncedCallback} from "use-debounce"
import {useSize} from "../hooks/useSize"

type Props = {
  onChange: (freeSpace: number, currentHeight: number) => void,
}

export function FillCheck({children, onChange}: PropsWithChildren<Props>): JSX.Element {
  const {observe: wrapperObserve, height: availableHeight} = useSize()
  const {observe: childObserve, height: currentHeight} = useSize()

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
    <div ref={wrapperObserve} className={wrapperStyles}>
      <div ref={childObserve}>{children}</div>
    </div>
  )
}
