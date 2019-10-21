import React from "react"
import {Table, Td, Tr} from "reactable"
import {withRouter} from 'react-router-dom'
import HttpService from "../../http/HttpService"
import DateUtils from "../../common/DateUtils"
import LoaderSpinner from "../../components/Spinner.js"
import HealthCheck from "../../components/HealthCheck.js"
import DialogMessages from "../../common/DialogMessages"
import {Glyphicon} from 'react-bootstrap'
import "../../stylesheets/processes.styl"
import filterIcon from '../../assets/img/search.svg'
import BaseProcesses from "./../BaseProcesses"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils";

class CustomProcesses extends BaseProcesses {

  constructor(props) {
    super(props)

    this.state = Object.assign({
      statusesLoaded: false,
      statuses: {},
    }, this.prepareState())
  }

  reloadProcesses(showLoader) {
    this.setState({showLoader: showLoader == null ? true : showLoader})

    HttpService.fetchCustomProcesses().then(response => {
      if (!this.state.showAddProcess) {
        this.setState({processes: response.data, showLoader: false})
      }
    }).catch(() => this.setState({showLoader: false}))
  }

  deploy = (process) => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(process.name), () => {
      return HttpService.deploy(process.name).then(() => this.reload())
    })
  }

  cancel = (process) => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.name), () => {
      return HttpService.cancel(process.name).then(() => this.reload())
    })
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input type="text" className="form-control" aria-describedby="basic-addon1" value={this.state.search}
                   onChange={this.onSearchChange}/>
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon}/>
            </span>
          </div>
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
          sortable={['name', 'category', 'modifyDate']}
          filterable={['name', 'category']}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'status', label: 'Status'},
            {key: 'deploy', label: 'Deploy'},
            {key: 'cancel', label: 'Cancel'}
          ]}
        >
          {
            this.state.processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
                  <Td column="modifyDate" title={DateUtils.formatAbsolutely(process.modificationDate)} className="centered-column">{DateUtils.formatRelatively(process.modificationDate)}</Td>
                  <Td column="status" className="status-column">
                    <div
                      className={this.processStatusClass(process)}
                      title={this.processStatusTitle(process)}
                    />
                  </Td>
                  <Td column="deploy" className="deploy-column">
                    <Glyphicon glyph="play" title="Deploy process" onClick={this.deploy.bind(this, process)}/>
                  </Td>
                  {
                    (this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses) === "status-running") ?
                      (
                        <Td column="cancel" className="cancel-column">
                          <Glyphicon glyph="stop" title="Cancel process" onClick={this.cancel.bind(this, process)}/>
                        </Td>
                      ) : []
                  }
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
  featuresSettings: state.settings.featuresSettings
})
export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CustomProcesses))
