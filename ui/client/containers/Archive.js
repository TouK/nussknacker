import React from "react";
import {render} from "react-dom";
import {browserHistory} from "react-router";
import {Table, Thead, Th, Tr, Td} from "reactable";
import {connect} from "react-redux";

import HttpService from "../http/HttpService";
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from "../common/DateUtils";
import LoaderSpinner from "../components/Spinner.js";

import "../stylesheets/processes.styl";
import filterIcon from '../assets/img/search.svg'
import createProcessIcon from '../assets/img/create-process.svg'
import editIcon from '../assets/img/edit-icon.png'

const Archive = React.createClass({

  getInitialState() {
    return {
      processes: [],
      filterVal: '',
      showLoader: true,
      showAddProcess: false,
      currentPage: 0,
      sort: { column: "id", direction: 1}
    }
  },

  componentDidMount() {
    const intervalIds = {
      reloadProcessesIntervalId: setInterval(() => this.reloadProcesses(), 10000)
    }
    this.setState(intervalIds)
    this.reloadProcesses();
  },

  componentWillUnmount() {
    if (this.state.reloadProcessesIntervalId) {
      clearInterval(this.state.reloadProcessesIntervalId)
    }
  },
  unarchiveProcess(processId) {
    HttpService.fetchArchivedProcesses().then ((fetchedProcesses) => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch( e => this.setState({ showLoader: false }))
  },

  reloadProcesses() {
    HttpService.fetchArchivedProcesses().then ((fetchedProcesses) => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch( e => this.setState({ showLoader: false }))
  },


  showProcess(process) {
    browserHistory.push('/visualization/' + process.id)
  },

  handleChange(event) {
    this.setState({filterVal: event.target.value});
  },

  getFilterValue() {
    return this.state.filterVal.toLowerCase();
  },

  render() {
    return (
      <div className="Page">
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input type="text" className="form-control" aria-describedby="basic-addon1"
                    value={this.state.filterVal} onChange={this.handleChange}/>
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
          </div>
          }
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
            <Th column="view" className="edit-column">View</Th>
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="id" className="blue-bar">{process.id}</Td>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>

                <Td column="modifyDate" className="date-column">{DateUtils.format(process.modificationDate)}</Td>
                <Td column="view" className="edit-column">
                  <img src={editIcon} className="edit-icon" title="View" onClick={e => this.showProcess(process)} />
                </Td>
              </Tr>
            )
          })}

        </Table>
      </div>
    )
  }
});

Archive.title = 'Archive Processes'
Archive.path = '/archivedProcesses'
Archive.header = 'Archive'


function mapState(state) {
  return {
    loggedUser: state.settings.loggedUser
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Archive);