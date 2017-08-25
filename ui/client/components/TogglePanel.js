import React, { Component, PropTypes } from 'react'
import cn from 'classnames';
import * as LoaderUtils from '../common/LoaderUtils'

import SvgDiv from './SvgDiv'
import '../stylesheets/togglePanel.styl'

export default class TogglePanel extends React.Component {

  static propTypes = {
    type: PropTypes.oneOf(['right', 'left']).isRequired,
    isOpened: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired,
  }

  render() {
    const { isOpened, onToggle, type } = this.props;
    const left = type === 'left' ?  isOpened : !isOpened;

    const iconFile = `arrows/arrow-${left ? 'left' : 'right'}.svg`
    return (
      <SvgDiv className={cn('togglePanel', type, { 'is-opened': isOpened})}
        onClick={onToggle} svgFile={iconFile}/>
    );
  }
}
