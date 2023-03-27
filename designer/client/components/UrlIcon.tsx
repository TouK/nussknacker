import React, {ComponentType, DetailedHTMLProps, ImgHTMLAttributes, PropsWithChildren, useEffect, useState} from "react"
import {absoluteBePath} from "../common/UrlUtils"
import {InlineSvg, InlineSvgProps} from "./SvgDiv"
import {ErrorBoundaryFallbackComponent} from "./common/ErrorBoundary"

interface ImageWithFallbackProps extends DetailedHTMLProps<ImgHTMLAttributes<HTMLImageElement>, HTMLImageElement> {
  src: string,
  FallbackComponent?: ComponentType,
}

function ImageWithFallback({src, FallbackComponent, ...props}: PropsWithChildren<ImageWithFallbackProps>): JSX.Element {
  const [error, setError] = useState(() => !src)

  useEffect(() => {
    setError(!src)
  }, [src])

  if (error && FallbackComponent) {
    return <FallbackComponent/>
  }

  return <img onError={() => setError(true)} src={src && absoluteBePath(src)} {...props}/>
}

export type UrlIconProps = InlineSvgProps & ImageWithFallbackProps

export default function UrlIcon({
  FallbackComponent = ErrorBoundaryFallbackComponent,
  ...props
}: UrlIconProps): JSX.Element {
  switch (true) {
    case /\.svg$/i.test(props.src):
      return <InlineSvg {...props} FallbackComponent={FallbackComponent}/>
    default:
      return <ImageWithFallback {...props} FallbackComponent={FallbackComponent}/>
  }
}
