import React from "react"
import {Table, Td, Tr} from "reactable"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import DateUtils from "../common/DateUtils"
import LoaderSpinner from "../components/Spinner.js"
import AddProcessDialog from "../components/AddProcessDialog.js"
import HealthCheck from "../components/HealthCheck.js"
import "../stylesheets/processes.styl"
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import {withRouter} from 'react-router-dom'
import BaseProcesses from "./BaseProcesses"
import {Glyphicon} from 'react-bootstrap'
import Select from 'react-select'
import ProcessUtils from "../common/ProcessUtils"

class SubProcesses extends BaseProcesses {
  queries = {
    isSubprocess: true,
    isArchived: false
  }

  constructor(props) {
    super(props)
    this.state = this.prepareState()
  }

  reload() {
    this.reloadProcesses(false)
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input
              type="text"
              placeholder="Filter by text.."
              className="form-control"
              aria-describedby="basic-addon1"
              value={this.state.search}
              onChange={this.onSearchChange}
            />
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>

          <div id="categories-filter" className="input-group">
            <Select
              isMulti
              isSearchable
              defaultValue={this.state.selectedCategories}
              closeMenuOnSelect={false}
              id="categories"
              className="form-select"
              options={this.props.filterCategories}
              placeholder="Select categories.."
              onChange={this.onCategoryChange}
              styles={this.customSelectStyles}
              theme={this.customSelectTheme}
            />
          </div>

          {
            this.props.loggedUser.isWriter ? (
            <div
              id="process-add-button"
              className="big-blue-button input-group"
              role="button"
              onClick={() => this.setState({showAddProcess : true})}
            >
              CREATE NEW SUBPROCESS
              <img id="add-icon" src={createProcessIcon} />
            </div>
            ) : null
          }
        </div>

        <AddProcessDialog
          onClose={() => this.setState({showAddProcess : false})}
          isSubprocess={true}
          isOpen={this.state.showAddProcess}
          visualizationPath={SubProcesses.path}
        />

        <LoaderSpinner show={this.state.showLoader} />

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
          columns = {[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'edit', label: 'Edit'}
          ]}
        >
          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" className="centered-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="edit" className="edit-column">
                  <Glyphicon glyph="edit" title="Edit subprocess" onClick={this.showProcess.bind(this, SubProcesses.path, process)} />
                </Td>
              </Tr>
            )
          })}
        </Table>
      </div>
    )
  }
}

SubProcesses.title = 'SubProcesses'
SubProcesses.path = '/subProcesses'
SubProcesses.header = 'Subprocesses'

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser)
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SubProcesses))