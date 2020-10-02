import {css} from "emotion"
import React, {MutableRefObject, PropsWithChildren, useEffect, useMemo} from "react"
import {createPortal} from "react-dom"

export function NestedPortal<I extends Element = Element>(props: PropsWithChildren<{parentSelector: string, parentRef: MutableRefObject<I>}>): JSX.Element {
  const {children, parentSelector, parentRef} = props
  const element = parentRef.current?.querySelector(parentSelector)

  const classname = useMemo(() => css({
    position: "relative",
  }), [])

  useEffect(() => {
    if (element && children) {
      element.classList.add(classname)
      return () => element.classList.remove(classname)
    }
  }, [children, element])

  if (!element?.childElementCount) {
    return null
  }

  return createPortal(children, element)
}
