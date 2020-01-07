import React from "react"
import {withRouter} from "react-router-dom"
import "../../stylesheets/processes.styl"
import HttpService from "../../http/HttpService"
import BaseAdminTab from "./BaseAdminTab"
import {Table, Td, Tr} from "reactable"
import filterIcon from "../../assets/img/search.svg"
import LoaderSpinner from "../../components/Spinner"
import SearchFilter from "../../components/table/SearchFilter"

class UnusedComponents extends BaseAdminTab {
  constructor(props) {
    super(props)

    this.state = Object.assign({
      unusedComponents: []
    }, this.prepareState())
  }

  componentDidMount() {
    HttpService.fetchUnusedComponents().then((response) => {
      this.setState({
        unusedComponents: _.map(response.data, (e) => {
          return {name: e}
        }), showLoader: false
      })
    })
  }

  render() {
    return (
      <div>
        <SearchFilter
          value={this.state.search}
          onChange={this.onSearchChange}/>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          sortable={["name"]}
          filterable={["name"]}
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

UnusedComponents.header = "Unused Components"
UnusedComponents.key = "unused-components"

export default withRouter(UnusedComponents)