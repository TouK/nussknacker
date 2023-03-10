import React from "react"

export default function HeaderIcon({className, icon}: {
  icon: string,
  className?: string,
}) {
  return (
    <div className={className} title="icon" dangerouslySetInnerHTML={{__html: icon}}/>
  )
}

