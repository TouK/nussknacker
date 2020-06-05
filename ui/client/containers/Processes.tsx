/* eslint-disable i18next/no-literal-string */
import * as queryString from "query-string"
import React from "react"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import LoaderSpinner from "../components/Spinner"
import {nkPath} from "../config"
import HttpService from "../http/HttpService"
import "../stylesheets/processes.styl"
import {BaseProcesses, BaseProcessesOwnProps, baseMapState} from "./BaseProcesses"
import {getTable} from "./getTable"
import {getColumns} from "./getColumns"
import history from "../history"
import {PageWithHealthCheck} from "./Page"
import {RouteComponentProps} from "react-router"
import {SearchItem, TableFilters} from "./TableFilters"
import {ProcessTableTools} from "./processTableTools"

export const path = `${nkPath}/processes`
export const header = "Processes"

const ProcessesPage = () => {
  const {isDeployed} = queryString.parse(history.location.search, {parseBooleans: true})
  return (
    <ProcessesComponent
      queries={{
        isSubprocess: false,
        isArchived: false,
      }}
      searchItems={[SearchItem.categories, SearchItem.isDeployed]}
      page={"processes"}
      shouldReloadStatuses={true}
      defaultState={{
        isDeployed: isDeployed as boolean,
        statusesLoaded: false,
        statuses: {},
      }}
    />
  )
}

class Processes extends BaseProcesses<Props> {
  private updateProcess(name, mutator) {
    const newProcesses = this.state.processes.slice()
    newProcesses.filter((process) => process.name === name).forEach(mutator)
    this.setState({processes: newProcesses})
  }

  private processNameChanged = (name, e) => {
    const newName = e.target.value
    this.updateProcess(name, process => process.editedName = newName)
  }

  private changeProcessName = (process, e) => {
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

  private handleBlur = (process) => {
    this.updateProcess(process.name, (process) => process.editedName = process.name)
  }

  render() {
    const {handleBlur, onSort, onPageChange, changeProcessName, processNameChanged} = this
    const {sort, statusesLoaded, processes, showLoader, statuses, page, search} = this.state

    return (
      <PageWithHealthCheck>
        <ProcessTableTools allowAdd>
          <TableFilters
            filters={this.searchItems}
            value={this.state}
            onChange={this.onFilterChange}
          />
        </ProcessTableTools>

        <LoaderSpinner show={this.state.showLoader}/>

        {getTable({
          columns: getColumns(),
          handleBlur,
          onSort,
          onPageChange,
          changeProcessName,
          processNameChanged,
          sort,
          statusesLoaded,
          processes,
          search,
          showLoader,
          statuses,
          page,
        })}
      </PageWithHealthCheck>
    )
  }

  static path = path
  static header = header
}

const mapDispatch = {}

type StateProps = ReturnType<typeof baseMapState> & typeof mapDispatch
type Props = StateProps & RouteComponentProps & BaseProcessesOwnProps

const ProcessesComponent = withRouter(connect(baseMapState, mapDispatch)(Processes))
export const Component = ProcessesPage

