import React, {PropsWithChildren} from "react"
import {Switch, useLocation} from "react-router-dom"
import {CSSTransition} from "react-transition-group"
import TransitionGroup from "react-transition-group/TransitionGroup"
import animations from "../stylesheets/animations.styl"

export function TransitionRouteSwitch(props: PropsWithChildren<unknown>) {
  const location = useLocation()
  return (
    <TransitionGroup className={animations.group}>
      <CSSTransition key={location.pathname} classNames={animations.fade} timeout={{enter: 300, exit: 300}}>
        <Switch location={location}>
          {props.children}
        </Switch>
      </CSSTransition>
    </TransitionGroup>
  )
}
