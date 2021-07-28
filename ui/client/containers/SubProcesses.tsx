import classNames from "classnames"
import React from "react"
import {useTranslation} from "react-i18next"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"
import "../stylesheets/processes.styl"
import styles from "../containers/processesTable.styl"
import {Page} from "./Page"
import {ProcessesTabData} from "./Processes"
import {Filterable, ProcessesList, RowsRenderer} from "./ProcessesList"
import {ProcessLink} from "./processLink"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"

function ShowProcessIcon({process}: {process: ProcessType}) {
  const {t} = useTranslation()
  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="edit" title={t("tableRowIcon-edit-subprocess", "Edit fragment")}/>
    </ProcessLink>
  )
}

const ElementsRenderer: RowsRenderer = ({processes}) => processes.map(process => (
  <Tr className="row-hover" key={process.name}>
    <Td column="name">{process.name}</Td>,
    <Td column="category">{process.processCategory}</Td>,
    <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>,
    <Td column="createdAt" className="centered-column" value={process.createdAt}><Date date={process.createdAt}/></Td>,
    <Td column="modifyDate" className="centered-column" value={process.modificationDate}><Date date={process.modificationDate}/></Td>,
    <Td column="edit" className={classNames("edit-column", styles.iconOnly)}><ShowProcessIcon process={process}/></Td>,
  </Tr>
))

const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy"]
const filterable: Filterable = ["name", "processCategory", "createdBy"]


function SubProcesses() {
  const {t} = useTranslation()
  const columns = [
    {key: "name", label: t("fragmentList.name", "Name")},
    {key: "category", label: t("fragmentList.category", "Category")},
    {key: "createdBy", label: t("fragmentList.createdBy", "Created by")},
    {key: "createdAt", label: t("fragmentList.createdAt", "Created at")},
    {key: "modifyDate", label: t("fragmentList.modifyDate", "Last modification")},
    {key: "edit", label: t("fragmentList.edit", "Edit")},
  ]
  return (
    <Page className={tabStyles.tabContentPage}>
      <ProcessesList
        defaultQuery={{isSubprocess: true, isArchived: false}}
        searchItems={[SearchItem.categories]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        allowAdd

        RowsRenderer={ElementsRenderer}
      />
    </Page>
  )
}

export const SubProcessesTabData = {
  path: `${ProcessesTabData.path}/subprocesses`,
  header: "Fragments",
  Component: SubProcesses,
}
