import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom'
import { Link } from 'react-router'
import { Table, Thead, Th, Tr, Td } from 'reactable'
import { browserHistory } from 'react-router'
import classNames from 'classnames'
import HttpService from '../http/HttpService'

import '../stylesheets/processes.styl'

import filterIcon from '../assets/img/filter-icon.svg'
import editIcon from '../assets/img/edit-icon.png'
import starFull from '../assets/img/star-full.svg'
import starEmpty from '../assets/img/star-empty.svg'

import LoaderSpinner from '../components/Spinner.js';

export const Processes = React.createClass({

  getInitialState() {
    return {
      processes: [],
      statuses: {},
      filterVal: '',
      favouriteList: new Set(),
      showLoader: true
    }
  },

  componentDidMount() {
    HttpService.fetchProcesses().then ((fetchedProcesses) => {
      this.setState({ processes: fetchedProcesses, showLoader: false })
    }).catch( e => this.setState({ showLoader: false }))
    HttpService.fetchProcessesStatus().then ((statuses) => {
      this.setState({ statuses: statuses, showLoader: false })
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

  render() {
    return (
      <div className="Page">
        <div id="process-filter" className="input-group">
          <input type="text" className="form-control" aria-describedby="basic-addon1"
                  value={this.state.filterVal} onChange={this.handleChange}/>
          <span className="input-group-addon" id="basic-addon1">
            <img id="search-icon" src={filterIcon} />
          </span>
        </div>
        <LoaderSpinner show={this.state.showLoader}/>
        <Table id="process-table" className="table"
           noDataText="No matching records found."
           hidden={this.state.showLoader}
           currentPage={0}
           itemsPerPage={10}
           pageButtonLimit={5}
           previousPageLabel="<"
           nextPageLabel=">"
           sortable={true}
           filterable={['id', 'name']}
           hideFilterInput
           filterBy={this.getFilterValue()}
        >

          <Thead>
            <Th column="id">ID</Th>
            <Th column="name">Process name</Th>
            <Th column="category">Category</Th>
            <Th column="createDate" className="date-column">Create date</Th>
            <Th column="edit" className="edit-column">Edit</Th>
            <Th column="metrics" className="metrics-column">Metrics</Th>
            <Th column="status" className="status-column">Status</Th>
            <Th column="favourite" className="favourite-column">
              <span>Favourite</span>
            </Th>
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="id" className="blue-bar">{process.id}</Td>
                <Td column="name">{process.name}</Td>
                <Td column="category">
                  <div>
                    {process.tags.map(function (tagi, tagIndex) {
                      return <div key={tagIndex} className="tagBlock">{tagi}</div>
                    })}
                  </div>
                </Td>
                <Td column="createDate" className="date-column">2016-08-10</Td>
                <Td column="edit" className="edit-column">
                  <img src={editIcon} className={this.editIconClass(process)} onClick={this.showProcess.bind(this, process)} />
                </Td>
                <Td column="metrics" className="metrics-column">
                  <span className="glyphicon glyphicon-stats" onClick={this.showMetrics.bind(this, process)}/>
                </Td>
                <Td column="status" className="status-column">
                  { this.state.statuses && _.get(this.state.statuses[process.name], 'isRunning') ? <div className="status-running"/> : null}
                </Td>
                <Td column="favourite" className="favourite-column">
                  <div className={this.isFavourite(process.id)}
                  onClick={this.setFavourite.bind(this, process.id)}></div>
                </Td>
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
