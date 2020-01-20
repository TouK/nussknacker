import * as  queryString from "query-string"
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import ActionsUtils from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import Date from "../components/common/Date"
import LoaderSpinner from "../components/Spinner.js"
import SearchFilter from "../components/table/SearchFilter"
import TableRowIcon from "../components/table/TableRowIcon"
import TableSelect from "../components/table/TableSelect"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import BaseProcesses from "./BaseProcesses"

class Archive extends BaseProcesses {
  queries = {
    isArchived: true,
  }

  searchItems = ["categories", "isSubprocess"]

  page = "archive"

  constructor(props) {
    super(props)

    const query = queryString.parse(this.props.history.location.search, {parseBooleans: true})

    this.state = Object.assign({
      selectedIsSubrocess: _.find(this.filterIsSubprocessOptions, {value: query.isSubprocess}),
    }, this.prepareState())
  }

  render() {
    return (
      <div className="Page">
        <div id="process-top-bar">
          <SearchFilter onChange={this.onSearchChange}
                        value={this.state.search}/>

          <TableSelect defaultValue={this.state.selectedCategories}
                       options={this.props.filterCategories}
                       placeholder={"Select categories.."}
                       onChange={this.onCategoryChange}
                       isMulti={true}
                       isSearchable={true}/>

          <TableSelect
            defaultValue={this.state.selectedIsSubrocess}
            options={this.filterIsSubprocessOptions}
            placeholder="Select process type.."
            onChange={this.onIsSubprocessChange}
            isMulti={false}
            isSearchable={false}/>
        </div>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          onSort={sort => this.setState({sort: sort})}
          onPageChange={page => this.setState({page: page})}
          noDataText="No matching records found."
          hidden={this.state.showLoader}
          currentPage={this.state.page}
          defaultSort={this.state.sort}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          sortable={["name", "category", "modifyDate"]}
          filterable={["name", "category"]}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "subprocess", label: "Subprocess"},
            {key: "modifyDate", label: "Last modification"},
            {key: "view", label: "View"},
          ]}
        >
          {
            this.state.processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
                  <Td column="subprocess" className="centered-column">
                    <Glyphicon glyph={process.isSubprocess ? "ok" : "remove"}/>
                  </Td>
                  <Td column="modifyDate"
                      className="centered-column"
                      value={process.modificationDate}>
                    <Date date={process.modificationDate}/>
                  </Td>
                  <Td column="view" className="edit-column">
                    <TableRowIcon
                      glyph="eye-open"
                      title={`Show ${  process.isSubprocess ? "subprocess" : "process"}`}
                      onClick={this.showProcess(process)}
                    />
                  </Td>
                </Tr>
              )
            })}
        </Table>
      </div>
    )
  }
}

Archive.path = `${nkPath}/archivedProcesses`
Archive.header = "Archive"

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser),
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Archive))
