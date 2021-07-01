import React, {PropsWithChildren, useEffect, useState} from "react"
import * as LoaderUtils from "../common/LoaderUtils"
import {absoluteBePath} from "../common/UrlUtils"

function UrlIcon({path, title, children}: PropsWithChildren<{path?: string, title?: string}>) {
  const [error, setError] = useState(!path)

  useEffect(() => {
    setError(!path)
  }, [path])

  if (error) {
    return <>{children}</>
  }

  try {
    const svgContent = LoaderUtils.loadSvgContent(path)
    return <div dangerouslySetInnerHTML={{__html: svgContent}}/>
  } catch (e) {
    return <img onError={() => setError(true)} src={absoluteBePath(path)} title={title} />
  }
}

export default UrlIcon
