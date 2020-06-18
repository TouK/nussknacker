/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import {ShowItem} from "./editItem"
import {Page} from "./Page"
import {ProcessesList, RowsRenderer} from "./ProcessesList"
import {SearchItem} from "./TableFilters"

export const path = `${nkPath}/archivedProcesses`
export const header = "Archive"

const ElementsRenderer: RowsRenderer = ({processes}) => processes.map(process => (
  <Tr className="row-hover" key={process.name}>
    <Td column="name">{process.name}</Td>
    <Td column="category">{process.processCategory}</Td>
    <Td column="subprocess" className="centered-column">
      <Glyphicon glyph={process.isSubprocess ? "ok" : "remove"}/>
    </Td>
    <Td
      column="modifyDate"
      className="centered-column"
      value={process.modificationDate}
    >
      <Date date={process.modificationDate}/>
    </Td>
    <Td column="view" className="edit-column">
      <ShowItem process={process}/>
    </Td>
  </Tr>
))

const sortable = ["name", "category", "modifyDate"]
const filterable = ["name", "category"]
const columns = [
  {key: "name", label: "Process name"},
  {key: "category", label: "Category"},
  {key: "subprocess", label: "Subprocess"},
  {key: "modifyDate", label: "Last modification"},
  {key: "view", label: "View"},
]

function Archive() {
  return (
    <Page>
      <ProcessesList
        defaultQuery={{isArchived: true}}
        searchItems={[SearchItem.categories, SearchItem.isSubprocess]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        RowsRenderer={ElementsRenderer}
      />
    </Page>
  )
}

export const Component = Archive
