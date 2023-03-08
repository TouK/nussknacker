import React, {ReactElement} from "react"

export default function HeaderIcon({className, icon}: {
  icon: ReactElement,
  className?: string,
}) {
  return (
    <div className={className} title="icon">{icon}</div>
  )
}

