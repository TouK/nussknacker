import React from "react"
import {Table, Td, Tr} from "reactable"
import {connect} from "react-redux"
import Select from 'react-select'
import HttpService from "../http/HttpService"
import ActionsUtils from "../actions/ActionsUtils"
import DateUtils from "../common/DateUtils"
import LoaderSpinner from "../components/Spinner.js"
import AddProcessDialog from "../components/AddProcessDialog.js"
import HealthCheck from "../components/HealthCheck.js"
import "../stylesheets/processes.styl"
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import {withRouter} from 'react-router-dom'
import ProcessUtils from "../common/ProcessUtils"
import BaseProcesses from "./BaseProcesses"
import {Glyphicon} from 'react-bootstrap'
import * as  queryString from 'query-string'
import {nkPath} from "../config";

class Processes extends BaseProcesses {
  queries = {
    isSubprocess: false,
    isArchived: false
  }

  searchItems = ['categories', 'isDeployed']
  shouldReloadStatuses = true

  deployedOptions = [
    {label: 'Show all processes', value: undefined},
    {label: 'Show only deployed processes', value: true},
    {label: 'Show only not deployed processes', value: false},
  ]

  constructor(props) {
    super(props)

    const query = queryString.parse(this.props.history.location.search, {parseBooleans: true})

    this.state = Object.assign({
      selectedDeployedOption: _.find(this.deployedOptions, {value: query.isDeployed}),
      statusesLoaded: false,
      statuses: {},
    }, this.prepareState())
  }

  updateProcess(name, mutator) {
    const newProcesses = this.state.processes.slice()
    newProcesses.filter((process) => process.name === name).forEach(mutator)
    this.setState({processes: newProcesses})
  }

  processNameChanged = (name, e) => {
    const newName = e.target.value;
    this.updateProcess(name, process => process.editedName = newName)
  }

  changeProcessName = (process, e) => {
    e.persist();
    if (e.key === "Enter" && process.editedName !== process.name) {
      HttpService.changeProcessName(process.name, process.editedName).then((isSuccess) => {
        if (isSuccess) {
          this.updateProcess(process.name, (process) => process.name = process.editedName)
          e.target.blur();
        }
      });
    }
  }

  handleBlur = (process, e) => {
    this.updateProcess(process.name, (process) => process.editedName = process.name)
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
              <img id="search-icon" src={filterIcon}/>
            </span>
          </div>

          <div id="categories-filter" className="input-group">
            <Select
              isMulti
              isSearchable
              defaultValue={this.state.selectedCategories}
              closeMenuOnSelect={false}
              className="form-select"
              options={this.props.filterCategories}
              placeholder="Select categories.."
              onChange={this.onCategoryChange}
              styles={this.customSelectStyles}
              theme={this.customSelectTheme}
            />
          </div>

          <div id="deployed-filter" className="input-group">
            <Select
              defaultValue={this.state.selectedDeployedOption}
              className="form-select"
              options={this.deployedOptions}
              placeholder="Select deployed info.."
              onChange={this.onDeployedChange}
              styles={this.customSelectStyles}
              theme={this.customSelectTheme}
            />
          </div>
          {
            this.props.loggedUser.isWriter() ? (
              <div
                id="process-add-button"
                className="big-blue-button input-group "
                role="button"
                onClick={() => this.setState({showAddProcess: true})}
              >
                CREATE NEW PROCESS
                <img id="add-icon" src={createProcessIcon}/>
              </div>
            ) : null
          }
        </div>

        <AddProcessDialog
          onClose={() => this.setState({showAddProcess: false})}
          isOpen={this.state.showAddProcess}
          isSubprocess={false}
          visualizationPath={Processes.path}
          message="Create new process"
        />

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
          sortable={['name', 'category', 'modifyDate']}
          filterable={['name', 'category']}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'status', label: 'Status'},
            {key: 'edit', label: 'Edit'},
            {key: 'metrics', label: 'Metrics'}
          ]}
        >
          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name" className="name-column" value={process.name}>
                  <input
                    value={process.editedName != null ? process.editedName : process.name}
                    className="transparent"
                    onKeyPress={(event) => this.changeProcessName(process, event)}
                    onChange={(event) => this.processNameChanged(process.name, event)}
                    onBlur={(event) => this.handleBlur(process, event)}
                  />
                </Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" className="centered-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="status" className="status-column">
                  <div
                    className={this.processStatusClass(process)}
                    title={this.processStatusTitle(process)}
                  />
                </Td>
                <Td column="edit" className="edit-column">
                  <Glyphicon glyph="edit" title="Edit process"
                             onClick={this.showProcess.bind(this, process)}/>
                </Td>
                <Td column="metrics" className="metrics-column">
                  <Glyphicon glyph="stats" title="Show metrics" onClick={this.showMetrics.bind(this, process)}/>
                </Td>
              </Tr>
            )
          })}
        </Table>
      </div>
    )
  }
}

Processes.path = `${nkPath}/processes`
Processes.header = 'Processes'

const mapState = state => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser)
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Processes))
