import React from "react"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import * as  queryString from "query-string"

class BaseAdminTab extends React.Component {

  onPageChange = (page) => {
    this.afterElementChange({page: page})
  }

  onSearchChange = (event) => {
    this.afterElementChange({search: event.target.value})
  }

  onSortChange = (sort) => {
    this.afterElementChange(sort)
  }

  prepareState() {
    const query = queryString.parse(this.props.history.location.search, {
      arrayFormat: "comma",
      parseNumbers: true,
      parseBooleans: true
    })

    return {
      sort: {column: query.column || "name", direction: query.direction || 1},
      search: query.search || "",
      page: query.page || 0,
      showLoader: true,
    }
  }

  afterElementChange(params, state) {
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState(Object.assign(state || {}, params))
  }
}

export default BaseAdminTab