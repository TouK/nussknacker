import React from "react"
import {Table, Td, Tr} from "reactable"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import DateUtils from "../common/DateUtils"
import LoaderSpinner from "../components/Spinner.js"
import AddProcessDialog from "../components/AddProcessDialog.js"
import HealthCheck from "../components/HealthCheck.js"
import "../stylesheets/processes.styl"
import {withRouter} from 'react-router-dom'
import BaseProcesses from "./BaseProcesses"
import {Glyphicon} from 'react-bootstrap'
import ProcessUtils from "../common/ProcessUtils"
import {nkPath} from "../config";
import AddProcessButton from "./AddProcessButton"
import TableSelect from "./TableSelect"
import SearchFilter from "./SearchFilter"

class SubProcesses extends BaseProcesses {
  page = 'subProcesses'

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
          processes={this.state.processes}
          subProcesses={this.state.subProcesses}
          archivedProcesses={this.state.archivedProcesses}/>

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
          sortable={['id', 'name', 'category', 'modifyDate']}
          filterable={['id', 'name', 'category']}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'edit', label: 'Edit'}
          ]}
        >
          {this.state.subProcesses.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" title={DateUtils.formatAbsolutely(process.modificationDate)}
                    className="centered-column">{DateUtils.formatRelatively(process.modificationDate)}</Td>
                <Td column="edit" className="edit-column">
                  <Glyphicon glyph="edit" title="Edit subprocess" onClick={this.showProcess.bind(this, process)}/>
                </Td>
              </Tr>
            )
          })}
        </Table>
      </div>
    )
  }
}

SubProcesses.header = 'Subprocesses'
SubProcesses.path = `${nkPath}/subProcesses`

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser)
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SubProcesses))