import React from "react"
import {withRouter} from 'react-router-dom'
import "../../stylesheets/processes.styl"
import HttpService from "../../http/HttpService"
import BaseAdminTab from "./BaseAdminTab"
import {Table, Td, Tr} from "reactable"
import filterIcon from "../../assets/img/search.svg"
import LoaderSpinner from "../../components/Spinner"

class UnusedComponents extends BaseAdminTab  {
  constructor(props) {
    super(props)

    this.state = Object.assign({
      unusedComponents: []
    }, this.prepareState())
  }

  componentDidMount() {
    HttpService.fetchUnusedComponents().then((response) => {
      this.setState({unusedComponents: _.map(response.data, (e) => {return {name: e}}), showLoader: false})
    })
  }

  render() {
    return (
      <div>
        <div id="table-filter" className="input-group">
          <input
            type="text"
            className="form-control"
            aria-describedby="basic-addon1"
            value={this.state.search}
            onChange={this.onSearchChange}
          />
          <span className="input-group-addon" id="basic-addon1">
            <img id="search-icon" src={filterIcon}/>
          </span>
        </div>

        <LoaderSpinner show={this.state.showLoader} />

        <Table
          className="esp-table"
          sortable={['name']}
          filterable={['name']}
          noDataText="No matching records found."
          hideFilterInput
          hidden={this.state.showLoader}
          onPageChange={this.onPageChange}
          onSort={this.onSortChange}
          currentPage={this.state.page}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: "name", label: "Component ID"}
          ]}
        >
          {
            this.state.unusedComponents.map((row, idx) => {
              return (
                <Tr key={idx}>
                  <Td column="name">{row.name}</Td>
                </Tr>
              )
            })
          }
        </Table>
      </div>
    )
  }
}

UnusedComponents.title = "Unused Components"
UnusedComponents.key = "unused-components"

export default withRouter(UnusedComponents)