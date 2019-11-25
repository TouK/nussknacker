import React from "react"
import PropTypes from "prop-types"

export default class HeaderIcon extends React.Component {

  static propTypes = {
    icon: PropTypes.string.isRequired
  }

  render() {
    const icon = this.props
    return (
      <div className="icon" title="icon" dangerouslySetInnerHTML={{__html: icon.icon}}/>
    )
  }
}