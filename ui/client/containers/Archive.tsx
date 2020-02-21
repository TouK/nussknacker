/* eslint-disable i18next/no-literal-string */
//TODO: remove (duplicated, not connected) strings
//TODO: remove inheritance
import * as  queryString from "query-string"
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import {mapDispatchWithEspActions} from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import Date from "../components/common/Date"
import LoaderSpinner from "../components/Spinner"
import SearchFilter from "../components/table/SearchFilter"
import TableRowIcon from "../components/table/TableRowIcon"
import TableSelect from "../components/table/TableSelect"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import BaseProcesses from "./BaseProcesses"
import i18next from "i18next"

export class Archive extends BaseProcesses {
  static path = `${nkPath}/archivedProcesses`

  queries = {
    isArchived: true,
  }
  searchItems = ["categories", "isSubprocess"]
  page = "archive"

  constructor(props) {
    super(props)

    const query = queryString.parse(this.props.history.location.search, {parseBooleans: true})

    this.state = Object.assign({
      selectedIsSubrocess: this.filterIsSubprocessOptions.find(({value}) => value === query.isSubprocess),
    }, this.prepareState())
  }

  render() {
    const sortable = ["name", "category", "modifyDate"]
    const filterable = ["name", "category"]
    const columns = [
      {key: "name", label: i18next.t("archive.table.columns.processName.title", "Process name")},
      {key: "category", label: i18next.t("archive.table.columns.category.title", "Category")},
      {key: "subprocess", label: i18next.t("archive.table.columns.subprocess.title", "Subprocess")},
      {key: "modifyDate", label: i18next.t("archive.table.columns.lastModification.title", "Last modification")},
      {key: "view", label: i18next.t("archive.table.columns.view.title", "View")},
    ]
    return (
      <div className="Page">
        <div id="process-top-bar">
          <SearchFilter
            onChange={this.onSearchChange}
            value={this.state.search}
          />

          <TableSelect
            defaultValue={this.state.selectedCategories}
            options={this.props.filterCategories}
            placeholder={i18next.t("table.filter.categories.placeholder", "Select categories...")}
            onChange={this.onCategoryChange}
            isMulti={true}
            isSearchable={true}
          />

          <TableSelect
            defaultValue={this.state.selectedIsSubrocess}
            options={this.filterIsSubprocessOptions}
            placeholder={i18next.t("table.filter.type.placeholder", "Select process type...")}
            onChange={this.onIsSubprocessChange}
            isMulti={false}
            isSearchable={false}
          />
        </div>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          onSort={sort => this.setState({sort: sort})}
          onPageChange={page => this.setState({page: page})}
          noDataText={i18next.t("table.noDataText", "No matching records found.")}
          hidden={this.state.showLoader}
          currentPage={this.state.page}
          defaultSort={this.state.sort}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          sortable={sortable}
          filterable={filterable}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={columns}
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
                  <Td
                    column="modifyDate"
                    className="centered-column"
                    value={process.modificationDate}
                  >
                    <Date date={process.modificationDate}/>
                  </Td>
                  <Td column="view" className="edit-column">
                    <TableRowIcon
                      glyph="eye-open"
                      title={process.isSubprocess ?
                        i18next.t("archive.table.columns.view.content.subprocess", "Show subprocess") :
                        i18next.t("archive.table.columns.view.content.process", "Show process")}
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

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser),
})

export default withRouter(connect(mapState, mapDispatchWithEspActions)(Archive))
