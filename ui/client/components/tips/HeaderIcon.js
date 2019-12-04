import React from "react"
import PropTypes from "prop-types"

export default class HeaderIcon extends React.Component {

  static propTypes = {
    icon: PropTypes.string.isRequired,
    className: PropTypes.string
  }

  render() {
    const {icon, className} = this.props
    return (
      <div className={className} title="icon" dangerouslySetInnerHTML={{__html: icon}}/>
    )
  }
}