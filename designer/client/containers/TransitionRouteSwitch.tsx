import React, {PropsWithChildren} from "react"
import {useRouteMatch} from "react-router"
import {Switch, useLocation} from "react-router-dom"
import {CSSTransition} from "react-transition-group"
import TransitionGroup from "react-transition-group/TransitionGroup"
import ErrorBoundary from "../components/common/ErrorBoundary"
import animations from "../stylesheets/animations.styl"

export function TransitionRouteSwitch(props: PropsWithChildren<unknown>): JSX.Element {
  const location = useLocation()
  const match = useRouteMatch<{root: string}>(`/:root/:rest?`)
  return (
    <TransitionGroup className={animations.group}>
      <CSSTransition key={match?.params?.root} classNames={animations.fade} timeout={{enter: 300, exit: 300}}>
        <ErrorBoundary>
          <Switch location={location}>
            {props.children}
          </Switch>
        </ErrorBoundary>
      </CSSTransition>
    </TransitionGroup>
  )
}
