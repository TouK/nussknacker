import filterIcon from "../../assets/img/search.svg"
import React from "react"

export default class SearchFilter extends React.Component {
  render() {
    const {value, onChange} = this.props

    return (
      <div id="table-filter" className="input-group">
        <input
          type="text"
          placeholder="Filter by text.."
          className="form-control"
          aria-describedby="basic-addon1"
          value={value}
          onChange={onChange}/>
        <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon}/>
        </span>
      </div>
    )
  }
}