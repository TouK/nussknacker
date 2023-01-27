import cn from "classnames"
import React, {useRef} from "react"
import {Table, TableComponentProperties} from "reactable"
import {NestedPortal} from "./NestedPortal"

export const TableElementsSelectors = {
  // eslint-disable-next-line i18next/no-literal-string
  pagination: ".reactable-pagination > tr > td",
}

type CustomTableProps = {
  extensions?: {
    [selector: string]: JSX.Element,
  },
}

export function TableWithDefaults(props: TableComponentProperties & CustomTableProps): JSX.Element {
  const {className, extensions = {}, ...passProps} = props
  const ref = useRef<HTMLDivElement>()

  return (
    <div ref={ref}>
      <Table
        className={cn("esp-table", className)}
        previousPageLabel={"<"}
        nextPageLabel={">"}
        pageButtonLimit={5}
        itemsPerPage={10}
        hideFilterInput={true}
        {...passProps}
      />
      {Object.entries(extensions).map(([selector, child]) => (
        <NestedPortal parentRef={ref} parentSelector={selector} key={selector}>{child}</NestedPortal>
      ))}
    </div>
  )
}
