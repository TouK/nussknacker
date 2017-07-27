import React, { Component, PropTypes } from 'react'
import cn from 'classnames';

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
    const right = type === 'right' ?  isOpened : !isOpened;


    return (
      <div
        className={cn('togglePanel', type, { 'is-opened': isOpened})}
        onClick={onToggle}
      >
         <div className={cn('arrow', { left, right })}/>
      </div>
    );
  }
}
