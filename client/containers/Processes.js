import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom'
import { Link } from 'react-router'
import { Table, Thead, Th, Tr, Td } from 'reactable'

import '../stylesheets/processes.styl'

export const Process = React.createClass({

    render: function() {

        // FIXME: mockupData to toss after trigering backend
        var mockupData = [
            {
              "name": "Proces 1",
              "tags": ["#tag1", "#tag3"],
              "status": "PROD",
              "dateCreated": "1-1-2015"
            }, {
              "name": "Proces 2",
              "tags": ["#tag1", "#tag3", "#tag6", "#tag9"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 3",
              "tags": ["#tag1", "#tag2"],
              "status": "PREP",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 4",
              "tags": ["#tag1", "#tag5"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 5",
              "tags": ["#tag2"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 6",
              "tags": ["#tag2", "#tag7", "#tag10"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 7",
              "tags": ["#tag10"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 8",
              "tags": ["#tag1", "#tag4", "#tag5"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }, {
              "name": "Proces 9",
              "tags": ["#tag8"],
              "status": "DRAFT",
              "dateCreated": "1-4-2015"
            }
        ];

        return (
            <div className="Page">
              <Table id="process-table" className="table"
                    noDataText="No matching records found."
                    itemsPerPage={5}
                    pageButtonLimit={5}
                    previousPageLabel="< "
                    nextPageLabel=" >"
                    sortable={true}
                    filterable={['name', 'status']}
              >

                <Thead>
                  <Th column="name">Nazwa procesu</Th>
                  <Th column="category">Kategorie</Th>
                  <Th column="status">Status</Th>
                  <Th column="createDate">Data utworzenia</Th>
                  <Th column="favourite">Ulubione</Th>
                </Thead>

                {mockupData.map(function(row, index){
                  return (
                    <Tr className="row-hover" key={index}>
                      <Td column="name" value="name">{row.name}</Td>
                      <Td column="category">
                        <div>
                          {row.tags.map(function(tagi, tagIndex){
                            return <div key={tagIndex} className="tagBlock">{tagi}</div>
                          })}
                        </div>
                      </Td>
                      <Td column="status">{row.status}</Td>
                      <Td column="createDate">{row.dateCreated}</Td>
                      <Td column="favourite"><input type="checkbox" /></Td>
                    </Tr>
                  )
                })}

              </Table>
            </div>
        )
    }
});

Process.title = 'Process'
Process.path = '/processes'
Process.header = 'Procesy'
