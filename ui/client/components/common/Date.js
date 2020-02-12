import React from "react"
import DateUtils from "../../common/DateUtils"

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
