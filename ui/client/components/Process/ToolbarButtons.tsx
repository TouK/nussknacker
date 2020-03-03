import React, {PropsWithChildren} from "react"

type Props = {
  small?: boolean,
}

export function ToolbarButtons(props: PropsWithChildren<Props>) {
  return (
    <div>
      {props.children}
    </div>
  )
}
