import React from "react"
import * as LoaderUtils from "../common/LoaderUtils"

export default class SvgDiv extends React.Component {

  render() {
    const {svgFile, ...rest} = this.props
    const icon = LoaderUtils.loadSvgContent(svgFile)
    //TODO: figure out how to do this without dangerously setting inner html...
    return (<div {...rest} dangerouslySetInnerHTML={{__html: icon}}/>)
  }
}
