import {css, cx} from "emotion"
import React from "react"
import {Link, LinkProps} from "react-router-dom"

export function PlainStyleLink(props: LinkProps): JSX.Element {
  const className = css({
    "&, &:hover, &:focus": {
      color: "inherit",
      textDecoration: "inherit",
    },
  })
  return (
    <Link {...props} className={cx(className, props.className)}/>
  )
}
