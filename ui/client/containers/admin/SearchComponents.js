import React from "react"
import {withRouter} from "react-router-dom"
import * as  queryString from "query-string"
import {Table, Td, Tr} from "reactable"
import "../../stylesheets/processes.styl"
import HttpService from "../../http/HttpService"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import LoaderSpinner from "../../components/Spinner"
import BaseAdminTab from "./BaseAdminTab"
import axios from "axios"
import SearchFilter from "../../components/table/SearchFilter"

class SearchComponents extends BaseAdminTab {
  constructor(props) {
    super(props)

    const query = queryString.parse(this.props.history.location.search)

    this.state = Object.assign({
      componentToFind: query.componentToFind,
      processesComponents: [],
      componentIds: []
    }, this.prepareState(), {showLoader: false})
  }

  componentDidMount() {
    let requests = [HttpService.fetchComponentIds()]
    if (this.state.componentToFind != null) {
      requests.push(HttpService.fetchProcessesComponents(this.state.componentToFind))
    }

    axios.all(requests).then(axios.spread((idsResponse, componentsResponse) => {
      this.setState({
        showLoader: false,
        componentIds: idsResponse.data,
        processesComponents: _.get(componentsResponse, "data", [])
      })
    }))
  }

  onComponentChange = (event) => {
    const componentToFind = event.target.value

    this.afterElementChange({componentToFind: componentToFind}, {showLoader: true})

    HttpService.fetchProcessesComponents(componentToFind).then((response) => {
      this.setState({
        processesComponents: response.data,
        showLoader: false
      })
    })
  }

  render() {
    return (
      <div>
        <select className="table-select" onChange={this.onComponentChange} value={this.state.componentToFind || 0}>
          <option disabled key={0} value={0}>-- select an option --</option>
          {
            this.state.componentIds.map((componentId, index) => {
              return (<option key={index} value={componentId}>{componentId}</option>)
            })
          }
        </select>

        <SearchFilter
          value={this.state.search}
          onChange={this.onSearchChange}/>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          sortable={["processName", "nodeId", "processCategory"]}
          filterable={["processName", "nodeId", "processCategory"]}
          noDataText="No matching records found."
          hideFilterInput
          hidden={this.state.showLoader || this.state.componentToFind == null}
          onPageChange={this.onPageChange}
          onSort={this.onSortChange}
          currentPage={this.state.page}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: "processName", label: "Process"},
            {key: "nodeId", label: "Node"},
            {key: "processCategory", label: "Category"},
            {key: "isDeployed", label: "Is deployed"},
          ]}
        >
          {
            this.state.processesComponents.map((row, idx) => {
              return (
                <Tr key={idx}>
                  <Td column="processName">{row.processName}</Td>
                  <Td column="nodeId">
                    <a target="_blank" href={VisualizationUrl.visualizationUrl(row.processName, row.nodeId)}>
                      {row.nodeId}
                    </a>
                  </Td>
                  <Td column="processCategory">{row.processCategory}</Td>
                  <Td column="isDeployed" className="centered-column">
                    <div className={row.isDeployed ? "status-running" : null}/>
                  </Td>
                </Tr>
              )
            })
          }
        </Table>
      </div>
    )
  }
}

SearchComponents.header = "Search components"
SearchComponents.key = "search-component"

export default withRouter(SearchComponents)