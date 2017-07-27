import React from "react";
import {render} from "react-dom";
import {Link, browserHistory} from "react-router";
import {Table, Thead, Th, Tr, Td} from "reactable";
import _ from "lodash";
import classNames from "classnames";
import {connect} from "react-redux";

import HttpService from "../http/HttpService";
import ActionsUtils from "../actions/ActionsUtils";
import DialogMessages from "../common/DialogMessages";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";
import AddProcessDialog from "../components/AddProcessDialog.js";
import HealthCheck from "../components/HealthCheck.js";

import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import editIcon from '../assets/img/edit-icon.png'

const Processes = React.createClass({

  getInitialState() {
    return {
      processes: [],
      statuses: {},
      filterVal: '',
      favouriteList: new Set(),
      showLoader: true,
      showAddProcess: false,
      currentPage: 0,
      sort: { column: "id", direction: 1}

    }
  },

  componentDidMount() {
    const intervalIds = {
      reloadStatusesIntervalId: setInterval(() => this.reloadProcesses(), 6000),
      reloadProcessesIntervalId: setInterval(() => this.reloadStatuses(), 10000)
    }
    this.setState(intervalIds)
    this.reloadProcessesAndStatuses();
  },

  reloadProcessesAndStatuses() {
    this.reloadProcesses();
    this.reloadStatuses();
  },

  componentWillUnmount() {
    if (this.state.reloadStatusesIntervalId) {
      clearInterval(this.state.reloadStatusesIntervalId)
    }
    if (this.state.reloadProcessesIntervalId) {
      clearInterval(this.state.reloadProcessesIntervalId)
    }
  },

  reloadProcesses() {
    HttpService.fetchProcesses().then ((fetchedProcesses) => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch( e => this.setState({ showLoader: false }))
  },

  reloadStatuses() {
    HttpService.fetchProcessesStatus().then ((statuses) => {
      if (!this.state.showAddProcess) {
        this.setState({ statuses: statuses, showLoader: false })
      }
    }).catch( e => this.setState({ showLoader: false }))
  },


  isGraph(process) {
    return process.processType == "graph"
  },

  showProcess(process) {
    if(this.isGraph(process)) {
      browserHistory.push('/visualization/' + process.id)
    }
  },

  showMetrics(process) {
    browserHistory.push('/metrics/' + process.id)
  },

  handleChange(event) {
    this.setState({filterVal: event.target.value});
  },

  deploy(process) {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(process.id), () => {
      return HttpService.deploy(process.id).then(this.reloadProcessesAndStatuses)
    })
  },

  stop(process) {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.id), () => {
      return HttpService.stop(process.id).then(this.reloadProcessesAndStatuses)
    })
  },

  getFilterValue() {
    return this.state.filterVal.toLowerCase();
  },

  setFavourite(process) {
    var favouriteArray = this.state.favouriteList;
    if ( favouriteArray.has(process) ){
      favouriteArray.delete(process);
    } else {
      favouriteArray.add(process);
    }
    this.setState({favouriteList: favouriteArray});
  },

  isFavourite(process){
    var isFavourite = classNames({
      'favourite-icon': true,
      'is-favourite': this.state.favouriteList.has(process)
    });
    return isFavourite;
  },

  editIconClass(process){
    return classNames({
      "edit-icon": true,
      "btn disabled edit-disabled": !this.isGraph(process),
    })
  },

  processStatusClass(process) {
    const processId = process.name;
    const shouldRun = process.currentlyDeployedAt.length > 0;
    const statusesKnown = this.state.statuses;
    const isRunning = statusesKnown && _.get(this.state.statuses[processId], 'isRunning');
    if (isRunning) {
      return "status-running";
    } else if (shouldRun) {
      return statusesKnown ? "status-notrunning" : "status-unknown"
    } else return null;
  },

  processStatusTitle(processStatusClass) {
    if (processStatusClass == "status-running") {
      return "Running"
    } else if (processStatusClass == "status-notrunning") {
      return "Not running"
    } else if (processStatusClass == "status-unknown") {
      return "Unknown state"
    } else {
      return null
    }
  },

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <div id="process-filter" className="input-group">
            <input type="text" className="form-control" aria-describedby="basic-addon1"
                    value={this.state.filterVal} onChange={this.handleChange}/>
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>
          {this.props.loggedUser.canWrite ? (
          <div id="process-add-button" className="input-group" role="button"
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
            <Th column="edit" className="edit-column">Edit</Th>
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
                  <div className={this.processStatusClass(process)} title={this.processStatusTitle(this.processStatusClass(process))}/>
                </Td>
                <Td column="favourite" className="favourite-column">
                  <div className={this.isFavourite(process.id)}
                  onClick={this.setFavourite.bind(this, process.id)}></div>
                </Td>
                <Td column="edit" className="edit-column">
                  <img src={editIcon} className={this.editIconClass(process)} title="Edit" onClick={this.showProcess.bind(this, process)} />
                </Td>
                <Td column="metrics" className="metrics-column">
                  <span className="glyphicon glyphicon-stats" title="Show metrics" onClick={this.showMetrics.bind(this, process)}/>
                </Td>

                {this.props.loggedUser.canDeploy ? (
                  <Td column="deploy" className="deploy-column">
                    <span className="glyphicon glyphicon-play" title="Deploy process" onClick={this.deploy.bind(this, process)}/>
                  </Td>
                ) : []}
                { (this.processStatusClass(process) == "status-running" && this.props.loggedUser.canDeploy) ?
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
});

Processes.title = 'Processes'
Processes.path = '/processes'
Processes.header = 'Processes'


function mapState(state) {
  return {
    loggedUser: state.settings.loggedUser
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Processes);
