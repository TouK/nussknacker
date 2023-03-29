import React, {ComponentProps, ReactNode, useState} from "react"
import {absoluteBePath} from "../../common/UrlUtils"
import styled from "@emotion/styled"

const Logo = styled.img({
  height: "1.5em",
  maxWidth: 150,
})

export function InstanceLogo({fallback = null, ...props}: { fallback?: ReactNode } & ComponentProps<typeof Logo>) {
  const [validLogo, setValidLogo] = useState(true)

  if (!validLogo) {
    return <>{fallback}</>
  }

  return (
    <Logo
      src={absoluteBePath("/assets/img/instance-logo.svg")}
      {...props}
      onError={() => setValidLogo(false)}
    />
  )
}
