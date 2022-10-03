import React, {PropsWithChildren, useEffect, useState} from "react"
import {absoluteBePath} from "../common/UrlUtils"
import SvgDiv from "./SvgDiv"

function UrlIcon({path, title, children}: PropsWithChildren<{ path?: string, title?: string }>): JSX.Element {
  const [error, setError] = useState(!path)

  useEffect(() => {
    setError(!path)
  }, [path])

  if (error) {
    return <>{children}</>
  }

  return (
    <SvgDiv svgFile={path}>
      <img onError={() => setError(true)} src={absoluteBePath(path)} title={title}/>
    </SvgDiv>
  )
}

export default UrlIcon
