import React from "react"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import ActionsUtils from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import AddProcessDialog from "../components/AddProcessDialog"
import Date from "../components/common/Date"
import HealthCheck from "../components/HealthCheck"
import LoaderSpinner from "../components/Spinner"
import AddProcessButton from "../components/table/AddProcessButton"
import SearchFilter from "../components/table/SearchFilter"
import TableRowIcon from "../components/table/TableRowIcon"
import TableSelect from "../components/table/TableSelect"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import BaseProcesses from "./BaseProcesses"

export class SubProcesses extends BaseProcesses {
  queries = {
    isSubprocess: true,
    isArchived: false,
  }

  page = "subProcesses"

  constructor(props) {
    super(props)
    this.state = this.prepareState()
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <SearchFilter onChange={this.onSearchChange}
                        value={this.state.search}/>

          <TableSelect defaultValue={this.state.selectedCategories}
                       options={this.props.filterCategories}
                       placeholder={"Select categories.."}
                       onChange={this.onCategoryChange}
                       isMulti={true}
                       isSearchable={true}/>

          <AddProcessButton loggedUser={this.props.loggedUser}
                            onClick={() => this.setState({showAddProcess: true})}/>
        </div>

        <AddProcessDialog
          onClose={() => this.setState({showAddProcess: false})}
          isSubprocess={true}
          isOpen={this.state.showAddProcess}
          visualizationPath={SubProcesses.path}
          message="Create new subprocess"
          clashedNames={this.state.clashedNames}/>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          onSort={this.onSort}
          onPageChange={this.onPageChange}
          noDataText="No matching records found."
          hidden={this.state.showLoader}
          currentPage={this.state.page}
          defaultSort={this.state.sort}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          sortable={["name", "category", "modifyDate", "createDate", "createdBy"]}
          filterable={["name", "category", "createdBy"]}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "createdBy", label: "Created by"},
            {key: "createdAt", label: "Created"},
            {key: "modifyDate", label: "Last modification"},
            {key: "edit", label: "Edit"},
          ]}
        >
          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
                <Td column="createdAt" className="centered-column" value={process.createdAt}>
                  <Date date={process.createdAt}/>
                </Td>
                <Td column="modifyDate" className="centered-column" value={process.modificationDate}>
                  <Date date={process.modificationDate}/>
                </Td>
                <Td column="edit" className="edit-column">
                  <TableRowIcon
                    glyph="edit"
                    title="Edit subprocess"
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

SubProcesses.header = "Subprocesses"
SubProcesses.path = `${nkPath}/subProcesses`

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser),
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SubProcesses))
