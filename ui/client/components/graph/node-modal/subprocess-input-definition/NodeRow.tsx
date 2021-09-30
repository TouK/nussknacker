import {cx} from "@emotion/css"
import React, {forwardRef} from "react"
import {NodeLabel} from "./NodeLabel"

type Props = React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> & {label?: string}

export const NodeRow = forwardRef<HTMLDivElement, Props>(function FieldRow(props, ref): JSX.Element {
  const {label, className, children, ...passProps} = props
  return (
    <div ref={ref} className={cx("node-row", className)} {...passProps}>
      {label && <NodeLabel label={label}/>}
      {children}
    </div>
  )
})

