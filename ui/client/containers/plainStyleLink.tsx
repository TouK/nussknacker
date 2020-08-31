import {css} from "emotion"
import React from "react"
import {Link, LinkProps} from "react-router-dom"

export function PlainStyleLink(props: LinkProps) {
  const className = css({
    "&, &:hover, &:focus": {
      color: "inherit",
      textDecoration: "inherit",
    },
  })
  return (
    <Link className={className} {...props}/>
  )
}
