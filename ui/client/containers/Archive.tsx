import classNames from "classnames"
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import "../stylesheets/processes.styl"
import styles from "../containers/processesTable.styl"
import {ShowItem} from "./editItem"
import {Page} from "./Page"
import {ProcessesTabData} from "./Processes"
import {Filterable, ProcessesList, RowsRenderer} from "./ProcessesList"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"
import {useTranslation} from "react-i18next";

const ElementsRenderer: RowsRenderer = ({processes}) => processes.map(process => (
  <Tr className="row-hover" key={process.name}>
    <Td column="name">{process.name}</Td>
    <Td column="category">{process.processCategory}</Td>
    <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
    <Td column="createdAt" className="centered-column" value={process.createdAt}><Date date={process.createdAt}/></Td>
    <Td column="actionDate" className="centered-column" value={process?.lastAction?.performedAt}><Date date={process?.lastAction?.performedAt}/></Td>
    <Td column="actionUser" className="centered-column" value={process?.lastAction?.user}>{process?.lastAction?.user}</Td>
    <Td column="subprocess" className="centered-column" value={process.isSubprocess}>
      <Glyphicon glyph={process.isSubprocess ? "ok" : "remove"}/>
    </Td>
    <Td column="view" className={classNames("edit-column", styles.iconOnly)}>
      <ShowItem process={process}/>
    </Td>
  </Tr>
))

const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy", "subprocess", "actionDate", "actionUser"]
const filterable: Filterable = ["name", "processCategory", "createdBy"]

function Archive() {
  const {t} = useTranslation()
  const columns = [
    {key: "name", label: t("archiveList.name", "Name")},
    {key: "category", label: t("archiveList.category", "Category")},
    {key: "createdBy", label: t("archiveList.createdBy", "Created by")},
    {key: "createdAt", label: t("archiveList.createdAt", "Created at")},
    {key: "actionDate", label: t("archiveList.archivedAt", "Archived at")},
    {key: "actionUser", label: t("archiveList.archivedBy", "Archived by")},
    {key: "subprocess", label: t("archiveList.subprocess", "Fragment")},
    {key: "view", label: t("archiveList.view", "View")},
  ]


  return (
    <Page className={tabStyles.tabContentPage}>
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

export const ArchiveTabData = {
  path: `${ProcessesTabData.path}/archived`,
  header: "Archive",
  Component: Archive,
}
