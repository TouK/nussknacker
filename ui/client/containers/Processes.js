import React from "react";
import {Table, Thead, Th, Tr, Td} from "reactable";
import {connect} from "react-redux";

import HttpService from "../http/HttpService";
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";
import AddProcessDialog from "../components/AddProcessDialog.js";
import HealthCheck from "../components/HealthCheck.js";

import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import editIcon from '../assets/img/edit-icon.png'
import PeriodicallyReloadingComponent from './../components/PeriodicallyReloadingComponent'
import ProcessesMixin from "../mixins/ProcessesMixin"

class Processes extends PeriodicallyReloadingComponent {

  constructor(props) {
    super(props)

    this.state = {
      processes: [],
      statuses: {},
      statusesLoaded: false,
      filterVal: '',
      showLoader: true,
      showAddProcess: false,
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
    HttpService.fetchProcesses().then (fetchedProcesses => {
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

  processNameChanged(id, e) {
    const newName = e.target.value;
    this.updateProcess(id, process => process.name = newName);
  }

  changeProcessName(process, e) {
    e.persist();
    if (e.key === "Enter" && process.id !== process.name) {
      HttpService.changeProcessName(process.id, process.name).then((isSuccess) => {
        if (isSuccess) {
          this.updateProcess(process.id, (process) => process.id = process.name);
          e.target.blur();
        }
      });
    }
  }

  updateProcess(id, mutator) {
    const newProcesses = this.state.processes.slice();
    newProcesses.filter((process) => process.id === id).forEach(mutator);
    this.setState({processes: newProcesses});
  }

  handleBlur(process, e) {
    this.updateProcess(process.id, (process) => process.name = process.id);
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
          {this.props.loggedUser.isWriter ? (
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
           sortable={['name', 'category', 'modifyDate']}
           filterable={['name', 'category']}
           hideFilterInput
           filterBy={this.getFilterValue()}

        >

          <Thead>
            <Th column="name">Process name</Th>
            <Th column="category">Category</Th>
            <Th column="modifyDate" className="date-column">Last modification</Th>
            <Th column="status" className="status-column">Status</Th>
            <Th column="edit" className="edit-column">Edit</Th>
            <Th column="metrics" className="metrics-column">Metrics</Th>
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name" value={process.id}>
                  <input value={process.name}
                         className="transparent"
                         onKeyPress={(event) => this.changeProcessName(process, event)}
                         onChange={(event) => this.processNameChanged(process.id, event)}
                         onBlur={(event) => this.handleBlur(process, event)}/>
                </Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="modifyDate" className="date-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="status" className="status-column">
                  <div className={this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses)} title={this.processStatusTitle(this.processStatusClass(process))}/>
                </Td>
                <Td column="edit" className="edit-column">
                  <img src={editIcon} title="Edit" onClick={this.showProcess.bind(this, process)} />
                </Td>
                <Td column="metrics" className="metrics-column">
                  <span className="glyphicon glyphicon-stats" title="Show metrics" onClick={this.showMetrics.bind(this, process)}/>
                </Td>
              </Tr>
            )
          })}

        </Table>
      </div>
    )
  }
}

Processes.title = 'Processes'
Processes.path = '/processes'
Processes.header = 'Processes'

const mapState = state => ({loggedUser: state.settings.loggedUser})
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Processes);
