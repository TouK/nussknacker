import {css, cx} from "emotion"
import {isString} from "lodash"
import React from "react"
import {Link, LinkProps} from "react-router-dom"

const externalUrlRe = /^(https?:)?\/\/\w/

export function isExternalUrl(to: unknown): to is string {
  return isString(to) && externalUrlRe.test(to)
}

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
    isExternalUrl(to) ?
      <a className={className} href={to} {...props}/> :
      <Link className={className} to={to} {...props}/>
}
