import React, { PropTypes, Component } from 'react';
import * as LoaderUtils from '../common/LoaderUtils'

export default class SvgDiv extends React.Component {

  render() {
    const icon = LoaderUtils.loadSvgContent(this.props.svgFile)
    //TODO: figure out how to do this without dangerously setting inner html...
    return (<div {...this.props} dangerouslySetInnerHTML={{__html: icon}} />)
  }
}