import * as  queryString from "query-string"
import React from "react"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import ActionsUtils from "../actions/ActionsUtils"
import ProcessUtils from "../common/ProcessUtils"
import HealthCheck from "../components/HealthCheck"
import LoaderSpinner from "../components/Spinner"
import {nkPath} from "../config"
import HttpService from "../http/HttpService"
import "../stylesheets/processes.styl"
import BaseProcesses from "./BaseProcesses"
import {getTable} from "./getTable"
import {getTableTools} from "./getTableTools"
import {getColumns} from "./getColumns"
import {goToProcess} from "../actions/nk/showProcess"

export class Processes extends BaseProcesses {
  queries = {
    isSubprocess: false,
    isArchived: false,
  }

  page = "processes"

  searchItems = ["categories", "isDeployed"]
  shouldReloadStatuses = true

  deployedOptions = [
    {label: "Show all processes", value: undefined},
    {label: "Show only deployed processes", value: true},
    {label: "Show only not deployed processes", value: false},
  ]
  getTableTools = getTableTools.bind(this)

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
    const newName = e.target.value
    this.updateProcess(name, process => process.editedName = newName)
  }

  changeProcessName = (process, e) => {
    e.persist()
    if (e.key === "Enter" && process.editedName !== process.name) {
      HttpService.changeProcessName(process.name, process.editedName).then((isSuccess) => {
        if (isSuccess) {
          this.updateProcess(process.name, (process) => process.name = process.editedName)
          e.target.blur()
        }
      })
    }
  }

  handleBlur = (process, e) => {
    this.updateProcess(process.name, (process) => process.editedName = process.name)
  }

  render() {
    const {handleBlur, onSort, onPageChange, changeProcessName, processNameChanged} = this
    const {sort, statusesLoaded, processes, search, showLoader, statuses, page} = this.state
    return (
      <div className="Page">
        <HealthCheck/>
        {this.getTableTools()}
        <LoaderSpinner show={this.state.showLoader}/>
        {getTable({
          columns: getColumns(),
          handleBlur,
          onSort,
          state: this.state,
          onPageChange,
          showProcess: (process) => () => goToProcess(process.name),
          changeProcessName,
          processNameChanged,
        }, {
          sort,
          statusesLoaded,
          processes,
          search,
          showLoader,
          statuses,
          page,
        })}
      </div>
    )
  }
}

export const path = `${nkPath}/processes`
export const header = "Processes"

//TODO: remove this statics
Processes.path = path
Processes.header = header

const mapState = state => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser),
})

export const Component = withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Processes))
