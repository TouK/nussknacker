import DateUtils from "Common/DateUtils"
import React from "react"

export default class Date extends React.Component {
  render() {
    const {date} = this.props
    return (
      <span title={DateUtils.formatAbsolutely(date)} className="date">
        {DateUtils.formatRelatively(date)}
      </span>
    )
  }
}