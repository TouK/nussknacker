import React, {ComponentType, SVGProps} from "react"

export default function ValidTip({icon: Icon, message}: {
  icon: ComponentType<SVGProps<SVGSVGElement>>,
  message: string,
}) {

  return (
    <div className={"valid-tip"}>
      <Icon className={"icon"}/>
      <span>{message}</span>
    </div>
  )
}
