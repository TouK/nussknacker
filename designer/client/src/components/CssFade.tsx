import {isFunction} from "lodash"
import React, {useCallback, useMemo} from "react"
import {CSSTransition} from "react-transition-group"
import {CSSTransitionProps} from "react-transition-group/CSSTransition"
import animations from "../stylesheets/animations.styl"

export function CssFade(props: Partial<CSSTransitionProps>) {
  const addEndListener = useCallback((nodeOrDone: HTMLElement | (() => void), done?: () => void) => {
    !isFunction(nodeOrDone) ?
      nodeOrDone.addEventListener("transitionend", done, false) :
      nodeOrDone()
  }, [])

  const timeout = useMemo(() => ({enter: 500, appear: 500, exit: 500}), [])

  return (
    <CSSTransition
      classNames={animations.fade}
      timeout={timeout}
      addEndListener={addEndListener}
      {...props}
    />
  )
}
