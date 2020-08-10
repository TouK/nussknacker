/* eslint-disable i18next/no-literal-string */
import * as  queryString from "query-string"
import React from "react"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import {defaultArrayFormat} from "../../common/VisualizationUrl"

class BaseAdminTab extends React.Component {

  onPageChange = (page) => {
    this.afterElementChange({page: page})
  }

  onSearchChange = (value) => {
    this.afterElementChange({search: value})
  }

  onSortChange = (sort) => {
    this.afterElementChange(sort)
  }

  prepareState() {
    const query = queryString.parse(this.props.history.location.search, {
      arrayFormat: defaultArrayFormat,
      parseNumbers: true,
      parseBooleans: true,
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
