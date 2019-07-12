import React from "react";
import {Table, Td, Tr} from "reactable";
import {withRouter} from 'react-router-dom'
import HttpService from "../http/HttpService";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";
import HealthCheck from "../components/HealthCheck.js";
import DialogMessages from "../common/DialogMessages";
import {Glyphicon} from 'react-bootstrap';
import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import BaseProcesses from "./BaseProcesses";

class CustomProcesses extends BaseProcesses {

  constructor(props) {
    super(props);

    this.state = {
      processes: [],
      statuses: {},
      statusesLoaded: false,
      showLoader: true,
      search: this.query.search || "",
      page: _.parseInt(this.query.page) || 0,
      sort: {column: this.query.column || "name", direction: _.parseInt(this.query.direction) || 1}
    }
  }

  reload() {
    this.reloadProcesses()
    this.reloadStatuses()
  }

  reloadProcesses() {
    HttpService.fetchCustomProcesses().then (fetchedProcesses => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch(() => this.setState({ showLoader: false }))
  }

  deploy = (process) => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(process.name), () => {
      return HttpService.deploy(process.name).then(() => this.reload())
    })
  }

  stop = (process) => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.name), () => {
      return HttpService.stop(process.name).then(() => this.reload())
    })
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck />
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input type="text" className="form-control" aria-describedby="basic-addon1" value={this.state.search} onChange={this.onSearchChange} />
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>
        </div>

        <LoaderSpinner show={this.state.showLoader} />

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
          sortable={['name', 'category', 'modifyDate']}
          filterable={['name', 'category']}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns = {[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'status', label: 'Status'},
            {key: 'deploy', label: 'Deploy'},
            {key: 'stop', label: 'Stop'}
          ]}
        >
          {
            this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" className="centered-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="status" className="status-column">
                  <div
                    className={this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses)}
                    title={this.processStatusTitle(this.processStatusClass(process))}
                  />
                </Td>
                <Td column="deploy" className="deploy-column">
                  <Glyphicon glyph="play" title="Deploy process" onClick={this.deploy.bind(this, process)} />
                </Td>
                {
                  (this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses) === "status-running") ?
                  (
                    <Td column="stop" className="stop-column">
                      <Glyphicon glyph="stop" title="Stop process" onClick={this.stop.bind(this, process)} />
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

CustomProcesses.title = "Custom Processes"

const mapState = state => ({loggedUser: state.settings.loggedUser})
export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CustomProcesses))
