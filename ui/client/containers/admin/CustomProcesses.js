import React from "react"
import {Glyphicon} from "react-bootstrap"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import ActionsUtils from "../../actions/ActionsUtils"
import * as DialogMessages from "../../common/DialogMessages"
import Date from "../../components/common/Date"
import HealthCheck from "../../components/HealthCheck"
import LoaderSpinner from "../../components/Spinner"
import SearchFilter from "../../components/table/SearchFilter"
import HttpService from "../../http/HttpService"
import "../../stylesheets/processes.styl"
import BaseProcesses from "./../BaseProcesses"
import ProcessStateIcon from "../../components/Process/ProcessStateIcon"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"

class CustomProcesses extends BaseProcesses {
  shouldReloadStatuses = true
  page = "custom"

  constructor(props) {
    super(props)

    this.state = Object.assign({
      statusesLoaded: false,
      statuses: {},
    }, this.prepareState())
  }

  reloadProcesses(showLoader) {
    this.showLoader(showLoader)

    HttpService.fetchCustomProcesses().then(response => {
      if (!this.state.showAddProcess) {
        this.setState({processes: response.data, showLoader: false})
      }
    }).catch(() => this.showLoader(false))
  }

  deploy = (process) => () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(process.name), () => {
      return HttpService.deploy(process.name).finally(() => this.reload())
    })
  }

  cancel = (process) => () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.name), () => {
      return HttpService.cancel(process.name).finally(() => this.reload())
    })
  }

  render() {
    const processState = getProcessState(this.state)
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <SearchFilter
            value={this.state.search}
            onChange={this.onSearchChange}
          />
        </div>

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
          sortable={["name", "category", "modifyDate", "createdAt"]}
          filterable={["name", "category"]}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "createdAt", label: "Created at"},
            {key: "modifyDate", label: "Last modification"},
            {key: "status", label: "Status"},
            {key: "deploy", label: "Deploy"},
            {key: "cancel", label: "Cancel"},
          ]}
        >
          {
            this.state.processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
                  <Td column="createdAt" className="centered-column" value={process.createdAt}>
                    <Date date={process.createdAt}/>
                  </Td>
                  <Td column="modifyDate" className="centered-column" value={process.modificationDate}>
                    <Date date={process.modificationDate}/>
                  </Td>
                  <Td column="status" className="status-column">
                    <ProcessStateIcon
                      process={process}
                      processState={processState(process)}
                      isStateLoaded={this.state.statusesLoaded}
                    />
                  </Td>
                  <Td column="deploy" className="deploy-column">
                    { ProcessStateUtils.canDeploy(processState(process)) ? (
                      <Glyphicon glyph="play" title="Deploy process" onClick={this.deploy(process)}/>
                    ): null
                    }
                  </Td>
                  <Td column="cancel" className="cancel-column">
                    { ProcessStateUtils.canCancel(processState(process)) ? (
                      <Glyphicon glyph="stop" title="Cancel process" onClick={this.cancel(process)}/>
                    ): null
                    }
                  </Td>
                </Tr>
              )
            })}
        </Table>
      </div>
    )
  }
}

CustomProcesses.header = "Custom Processes"
CustomProcesses.key = "custom-processes"

const mapState = state => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CustomProcesses))
