import React from "react";
import {Table, Thead, Th, Tr, Td} from "reactable";

import HttpService from "../http/HttpService";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";
import HealthCheck from "../components/HealthCheck.js";
import AddProcessDialog from "../components/AddProcessDialog.js";

import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import PeriodicallyReloadingComponent from './../components/PeriodicallyReloadingComponent'
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import ProcessesMixin from "../mixins/ProcessesMixin";

class CustomProcesses extends PeriodicallyReloadingComponent {

  constructor(props) {
    super(props);

    this.state = {
      processes: [],
      statuses: {},
      statusesLoaded: false,
      filterVal: '',
      showLoader: true,
      currentPage: 0,
      sort: { column: "id", direction: 1}
    }

    Object.assign(this, ProcessesMixin)
  }

  reload() {
    this.reloadStatuses();
  }

  onMount() {
    this.reloadProcesses();
  }

  reloadProcesses() {
    HttpService.fetchCustomProcesses().then (fetchedProcesses => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch(this.setState({ showLoader: false }))
  }

  reloadStatuses() {
    HttpService.fetchProcessesStatus().then (statuses => {
      if (!this.state.showAddProcess) {
        this.setState({ statuses: statuses, showLoader: false, statusesLoaded: true })
      }
    }).catch(this.setState({ showLoader: false }))
  }

  handleChange(event) {
    this.setState({filterVal: event.target.value});
  }

  getFilterValue() {
    return this.state.filterVal.toLowerCase();
  }

  deploy(process) {
    this.props.actions.toggleConfirmDialog(true, HttpService.deploy(process.id), () => {
      return HttpService.deploy(process.id).then(this.reload)
    })
  }

  stop(process) {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.id), () => {
      return HttpService.stop(process.id).then(this.reload)
    })
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input type="text" className="form-control" aria-describedby="basic-addon1"
                   value={this.state.filterVal} onChange={e => this.handleChange(e)}/>
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>
          {this.props.loggedUser.canWrite ? (
            <div id="process-add-button" className="big-blue-button input-group " role="button"
                 onClick={() => this.setState({showAddProcess : true})}>CREATE NEW PROCESS
              <img id="add-icon" src={createProcessIcon} />
            </div>) : null
          }
        </div>
        <AddProcessDialog onClose={() => this.setState({showAddProcess : false})} isOpen={this.state.showAddProcess} isSubprocess={false}/>
        <LoaderSpinner show={this.state.showLoader}/>
        <Table className="esp-table"
               onSort={sort => this.setState({sort: sort})}
               onPageChange={currentPage => this.setState({currentPage: currentPage})}
               noDataText="No matching records found."
               hidden={this.state.showLoader}
               currentPage={this.state.currentPage}
               defaultSort={this.state.sort}
               itemsPerPage={10}
               pageButtonLimit={5}
               previousPageLabel="<"
               nextPageLabel=">"
               sortable={['id', 'name', 'category', 'modifyDate']}
               filterable={['id', 'name', 'category']}
               hideFilterInput
               filterBy={this.getFilterValue()}

        >

          <Thead>
          <Th column="id">ID</Th>
          <Th column="name">Process name</Th>
          <Th column="category">Category</Th>
          <Th column="modifyDate" className="date-column">Last modification</Th>
          <Th column="status" className="status-column">Status</Th>
          <Th column="metrics" className="metrics-column">Metrics</Th>
          {this.props.loggedUser.canDeploy ? (
            <Th column="deploy" className="deploy-column">Deploy</Th>
          ) : []}
          {this.props.loggedUser.canDeploy ? (
            <Th column="stop" className="stop-column">Stop</Th>
          ) : []}
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="id" className="blue-bar">{process.id}</Td>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" className="date-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="status" className="status-column">
                  <div className={this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses)} title={this.processStatusTitle(this.processStatusClass(process))}/>
                </Td>
                <Td column="metrics" className="metrics-column">
                  <span className="glyphicon glyphicon-stats" title="Show metrics" onClick={this.showMetrics.bind(this, process)}/>
                </Td>
                { this.props.loggedUser.canDeploy ? (
                  <Td column="deploy" className="deploy-column">
                    <span className="glyphicon glyphicon-play" title="Deploy process" onClick={this.deploy.bind(this, process)}/>
                  </Td>
                ) : []}
                { (this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses) === "status-running" && this.props.loggedUser.canDeploy) ?
                  (
                    <Td column="stop" className="stop-column">
                      <span className="glyphicon glyphicon-stop" title="Stop process" onClick={this.stop.bind(this, process)}/>
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
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CustomProcesses);
