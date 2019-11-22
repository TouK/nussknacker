import React from "react"

export default class HeaderIcon extends React.Component {

  render() {
    const icon = this.props
    return (
      <div className="icon" title="icon" dangerouslySetInnerHTML={{__html: icon.icon}}>
      </div>
    )
  }
}