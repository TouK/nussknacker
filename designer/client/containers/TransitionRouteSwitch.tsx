import React, {PropsWithChildren} from "react"
import {CSSTransition} from "react-transition-group"
import TransitionGroup from "react-transition-group/TransitionGroup"
import ErrorBoundary from "../components/common/ErrorBoundary"
import animations from "../stylesheets/animations.styl"
import {Routes, useLocation, useMatch} from "react-router-dom"

export function TransitionRouteSwitch(props: PropsWithChildren<unknown>): JSX.Element {
  const location = useLocation()
  const match = useMatch(`/:root/*`)
  return (
    <TransitionGroup className={animations.group}>
      <CSSTransition key={match?.params?.root} classNames={animations.fade} timeout={{enter: 300, exit: 300}}>
        <ErrorBoundary>
          <Routes location={location}>
            {props.children}
          </Routes>
        </ErrorBoundary>
      </CSSTransition>
    </TransitionGroup>
  )
}
