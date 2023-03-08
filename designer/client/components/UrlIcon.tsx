import React, {ComponentType, DetailedHTMLProps, ImgHTMLAttributes, PropsWithChildren, useEffect, useState} from "react"
import {absoluteBePath} from "../common/UrlUtils"
import {InlineSvg, InlineSvgProps} from "./SvgDiv"

interface ImageWithFallbackProps extends DetailedHTMLProps<ImgHTMLAttributes<HTMLImageElement>, HTMLImageElement> {
  src: string,
  FallbackComponent?: ComponentType,
}

function ImageWithFallback({src, FallbackComponent, ...props}: PropsWithChildren<ImageWithFallbackProps>): JSX.Element {
  const [error, setError] = useState(!src)

  useEffect(() => {
    setError(!src)
  }, [src])

  if (error && FallbackComponent) {
    return <FallbackComponent/>
  }

  return <img onError={() => setError(true)} src={absoluteBePath(src)} {...props}/>
}

export type UrlIconProps = InlineSvgProps & ImageWithFallbackProps

export default function UrlIcon(props: UrlIconProps): JSX.Element {
  switch (true) {
    case /\.svg$/i.test(props.src):
      return <InlineSvg {...props}/>
    default:
      return <ImageWithFallback {...props}/>
  }
}
