import cn from "classnames"
import PropTypes from "prop-types"
import React from "react"
import "../stylesheets/togglePanel.styl"

import SvgDiv from "./SvgDiv"

export default class TogglePanel extends React.Component {

  static propTypes = {
    type: PropTypes.oneOf(["right", "left"]).isRequired,
    isOpened: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired,
  }

  render() {
    const {isOpened, onToggle, type} = this.props
    const left = type === "left" ?  isOpened : !isOpened
    const iconFile = `arrows/arrow-${left ? "left" : "right"}.svg`
    return (
      <SvgDiv className={cn("togglePanel", type, {"is-opened": isOpened})} onClick={onToggle} svgFile={iconFile}/>
    )
  }
}
