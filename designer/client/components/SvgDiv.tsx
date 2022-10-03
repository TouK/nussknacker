import React, {PropsWithChildren} from "react"
import * as LoaderUtils from "../common/LoaderUtils"

type Props = {
  svgFile: string,
  className?: string,
  title?: string,
  onClick?: () => void,
}

export default function SvgDiv(props: PropsWithChildren<Props>): JSX.Element {
  const {children, svgFile, ...rest} = props
  try {
    const icon = LoaderUtils.loadSvgContent(svgFile)
    //TODO: figure out how to do this without dangerously setting inner html...
    return (<div {...rest} dangerouslySetInnerHTML={{__html: icon}}/>)
  } catch (error) {
    return <>{children}</>
  }
}
