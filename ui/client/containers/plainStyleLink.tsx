import {css} from "emotion"
import React from "react"
import {Link, LinkProps} from "react-router-dom"

export function PlainStyleLink({disabled, to, ...props}: LinkProps & {disabled?: boolean}): JSX.Element {
  const className = css({
    "&, &:hover, &:focus": {
      color: "inherit",
      textDecoration: "inherit",
    },
  })
  return disabled ?
    <span className={className} {...props}/> :
    <Link className={className} to={to} {...props}/>
}
