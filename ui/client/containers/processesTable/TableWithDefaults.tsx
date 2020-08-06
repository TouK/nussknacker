import cn from "classnames"
import React from "react"
import {Table, TableComponentProperties} from "reactable"

export function TableWithDefaults({className, ...props}: TableComponentProperties) {
  return (
    <Table
      className={cn("esp-table", className)}
      previousPageLabel={"<"}
      nextPageLabel={">"}
      pageButtonLimit={5}
      itemsPerPage={10}
      hideFilterInput={true}
      {...props}
    />
  )
}
