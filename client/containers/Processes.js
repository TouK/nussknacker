import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom'
import { Link } from 'react-router'
import { Table, Thead, Th, Tr, Td } from 'reactable'
import $ from 'jquery'
import { browserHistory } from 'react-router'

import '../stylesheets/processes.styl'
import appConfig from 'appConfig'

export const Processes = React.createClass({

  getInitialState() {
    return {
      processes: [],
      filterVal: ''
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

  render() {
    return (
      <div className="Page">
        <div id="process-filter" className="input-group">
          <input type="text" className="form-control" aria-describedby="basic-addon1"
                  value={this.state.filterVal} onChange={this.handleChange}/>
          <span className="input-group-addon" id="basic-addon1">
            <img id="search-icon" src="assets/img/filter-icon.svg" />
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
            <Th column="id">Id</Th>
            <Th column="name">Process name</Th>
            <Th column="category">Category</Th>
            <Th column="createDate">Create date</Th>
            <Th column="favourite">Favourite</Th>
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
                <Td column="createDate">2016-08-10</Td>
                <Td column="favourite"><input type="checkbox"/></Td>
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
