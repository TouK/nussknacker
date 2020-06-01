import {TransitionGroup, CSSTransition} from "react-transition-group"
import {Route} from "react-router-dom"
import React from "react"
import {RouteProps} from "react-router"
import ErrorBoundary from "react-error-boundary"

export function FadeRoute(props: RouteProps) {
  return (
    <ErrorBoundary>
      <TransitionGroup>
        <CSSTransition key={JSON.stringify(props.path)} classNames="fade" timeout={{enter: 300, exit: 300}}>
          <Route {...props}/>
        </CSSTransition>
      </TransitionGroup>
    </ErrorBoundary>
  )
}
