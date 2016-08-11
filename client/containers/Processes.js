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
      processes: []
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

  render() {
    return (
      <div className="Page">
        <Table id="process-table" className="table"
               noDataText="No matching records found."
               itemsPerPage={5}
               pageButtonLimit={5}
               previousPageLabel="< "
               nextPageLabel=" >"
               sortable={true}
               currentPage="0"
               filterable={['name']}
        >

          <Thead>
          <Th column="id">Id</Th>
          <Th column="name">Process name</Th>
          <Th column="category">Category</Th>
          <Th column="createDate">Create date</Th>
          <Th column="favourite">Favorite</Th>
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
