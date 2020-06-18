import {isEmpty} from "lodash"
import React from "react"
import SvgDiv from "../SvgDiv"
import {FilterProps} from "./FilterTypes"

export default function SearchFilter(props: FilterProps) {
  const {value, onChange} = props
  const fillIconClass = isEmpty(value) ? "search-icon-fill" : "search-icon-fill-filter"
  return (
    <div id="table-filter" className="input-group">
      <div className="search-container">
        <input
          type="text"
          placeholder="Filter by text..."
          className="form-control"
          value={value || ""}
          onChange={e => onChange(`${e.target.value}`)}
        />
        <SvgDiv className={`search-icon ${fillIconClass}`} svgFile="search.svg"/>
      </div>
    </div>
  )
}
