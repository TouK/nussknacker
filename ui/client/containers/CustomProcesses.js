import React from "react";
import {Table, Thead, Th, Tr, Td} from "reactable";

import HttpService from "../http/HttpService";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";
import HealthCheck from "../components/HealthCheck.js";
import DialogMessages from "../common/DialogMessages";

import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
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
      sort: { column: "Process name", direction: 1}
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
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(process.name), () => {
      return HttpService.deploy(process.name).then(() => this.reload())
    })
  }

  stop(process) {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(process.name), () => {
      return HttpService.stop(process.name).then(() => this.reload())
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
        </div>
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
               sortable={['Process name', 'Category', 'Last modification']}
               filterable={['Process name', 'Category']}
               hideFilterInput
               filterBy={this.getFilterValue()}

        >

          <Thead>
          <Th column="name">Process name</Th>
          <Th column="category">Category</Th>
          <Th column="modifyDate" className="date-column">Last modification</Th>
          <Th column="status" className="status-column">Status</Th>
          <Th column="deploy" className="deploy-column">Deploy</Th>
          <Th column="stop" className="stop-column">Stop</Th>
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="Process name">{process.name}</Td>
                <Td column="Category">{process.processCategory}</Td>
                <Td column="Last modification" className="date-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="Status" className="status-column">
                  <div className={this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses)} title={this.processStatusTitle(this.processStatusClass(process))}/>
                </Td>
                <Td column="Deploy" className="deploy-column">
                  <span className="glyphicon glyphicon-play" title="Deploy process" onClick={this.deploy.bind(this, process)}/>
                </Td>
                { (this.processStatusClass(process, this.state.statusesLoaded, this.state.statuses) === "status-running") ?
                  (
                    <Td column="Stop" className="stop-column">
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
