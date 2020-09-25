import {css, cx} from "emotion"
import React from "react"
import {Link, LinkProps} from "react-router-dom"

export function PlainStyleLink({disabled, to, ...props}: LinkProps & {disabled?: boolean}): JSX.Element {
  const className = cx(
    css({
      "&, &:hover, &:focus": {
        color: "inherit",
        textDecoration: "inherit",
      },
    }), 
    props.className,
  )
  return disabled ?
    <span className={className} {...props}/> :
    <Link className={className} to={to} {...props}/>
}
