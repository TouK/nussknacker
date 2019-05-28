import React from "react";
import {Table, Thead, Th, Td, Tr} from "reactable";
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
      sort: { column: "name", direction: 1}
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
        fetchedProcesses = _.clone(fetchedProcesses);
        fetchedProcesses.forEach((process) => process.editedName = process.name);
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

  processNameChanged(name, e) {
    const newName = e.target.value;
    this.updateProcess(name, process => process.editedName = newName);
  }

  changeProcessName(process, e) {
    e.persist();
    if (e.key === "Enter" && process.editedName !== process.name) {
      HttpService.changeProcessName(process.name, process.editedName).then((isSuccess) => {
        if (isSuccess) {
          this.updateProcess(process.name, (process) => process.name = process.editedName);
          e.target.blur();
        }
      });
    }
  }

  updateProcess(name, mutator) {
    const newProcesses = this.state.processes.slice();
    newProcesses.filter((process) => process.name === name).forEach(mutator);
    this.setState({processes: newProcesses});
  }

  handleBlur(process, e) {
    this.updateProcess(process.name, (process) => process.editedName = process.name);
  }

  render() {
    return (
      <div className="Page">
        <HealthCheck/>
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input type="text"
                   className="form-control"
                   aria-describedby="basic-addon1"
                   value={this.state.filterVal}
                   onChange={e => this.handleChange(e)}
            />
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>
          {
            this.props.loggedUser.isWriter ? (
              <div id="process-add-button"
                   className="big-blue-button input-group "
                   role="button"
                   onClick={() => this.setState({showAddProcess : true})}
              >
                CREATE NEW PROCESS
                <img id="add-icon" src={createProcessIcon} />
              </div>
            ) : null
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
               columns = {[
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
                <Td column="name" value={process.name}>
                  <input value={process.editedName}
                         className="transparent"
                         onKeyPress={(event) => this.changeProcessName(process, event)}
                         onChange={(event) => this.processNameChanged(process.name, event)}
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
