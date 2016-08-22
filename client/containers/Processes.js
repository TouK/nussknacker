import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom'
import { Link } from 'react-router'
import { Table, Thead, Th, Tr, Td } from 'reactable'
import $ from 'jquery'
import { browserHistory } from 'react-router'

import '../stylesheets/processes.styl'
import appConfig from 'appConfig'

import starFull from '../assets/img/star-full.svg'
import starEmpty from '../assets/img/star-empty.svg'
import filterIcon from '../assets/img/filter-icon.svg'

export const Processes = React.createClass({

  getInitialState() {
    return {
      processes: [],
      filterVal: '',
      favouriteList: ["highPremiumUsage", "process"]
    }
  },

  componentDidMount() {
    $.get(appConfig.API_URL + "/processes", (fetchedProcesses) => {
      this.setState({processes: fetchedProcesses})
    })
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

  getFavouriteIcon(id) {
    if (this.state.favouriteList.includes(id)){
      return starFull
    } else {
      return starEmpty;
    }
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
        <Table id="process-table" className="table"
               noDataText="No matching records found."
               itemsPerPage={10}
               pageButtonLimit={5}
               previousPageLabel="< "
               nextPageLabel=" >"
               sortable={true}
               currentPage="0"
               filterable={['id', 'name']}
               hideFilterInput
               filterBy={this.getFilterValue()}
        >

          <Thead>
            <Th column="id">ID</Th>
            <Th column="name">Process name</Th>
            <Th column="category">Category</Th>
            <Th column="createDate" className="date-column">Create date</Th>
            <Th column="favourite" className="favourite-column">
              <span>Favourite</span>
              <img src={this.getFavouriteIcon(process.id)} />
            </Th>
          </Thead>

          {this.state.processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index} onClick={this.showProcess.bind(this, process)}>
                <Td column="id">{process.id}</Td>
                <Td column="name">{process.name}</Td>
                <Td column="category">
                  <div>
                    {process.tags.map(function (tagi, tagIndex) {
                      return <div key={tagIndex} className="tagBlock">{tagi}</div>
                    })}
                  </div>
                </Td>
                <Td column="createDate" className="date-column">2016-08-10</Td>
                <Td column="favourite" className="favourite-column">
                  <img  src={this.getFavouriteIcon(process.id)} />
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
