import React, {ReactElement} from "react"
import HeaderIcon from "./HeaderIcon"

export default function ValidTip({icon, message}: {
  icon: ReactElement,
  message: string,
}) {

  return (
    <div className={"valid-tip"}>
      <HeaderIcon className={"icon"} icon={icon}/>
      <span>{message}</span>
    </div>
  )
}
