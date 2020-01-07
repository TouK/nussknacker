import React from "react"
import SvgDiv from "../SvgDiv";

export default class SearchFilter extends React.Component {
  render() {
    const {value, onChange} = this.props
    const fillIconClass = _.isEmpty(value) ? "search-icon-fill" : "search-icon-fill-filter"
    return (
      <div id="table-filter" className="input-group">
        <div className="search-container">
          <input
            type="text"
            placeholder="Filter by text.."
            className="form-control"
            value={value}
            onChange={onChange}
          />
          <SvgDiv className={`search-icon ${  fillIconClass}`} svgFile="search.svg"/>
        </div>
      </div>
    )
  }
}