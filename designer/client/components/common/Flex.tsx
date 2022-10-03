import React from "react"

type DivProps = React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement>

type OwnProps = { flex?: number }

export function Flex({style, flex, ...props}: DivProps & OwnProps) {
  return (
    <div
      {...props}
      style={{
        display: "flex",
        alignItems: "center",
        height: "100%",
        flex,
        ...style,
      }}
    />
  )
}
