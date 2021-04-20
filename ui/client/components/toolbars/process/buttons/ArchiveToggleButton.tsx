import React from "react"
import {useSelector} from "react-redux"
import {isArchived} from "../../../../reducers/selectors/graph"
import ArchiveButton from "../buttons/ArchiveButton"
import UnArchiveButton from "../buttons/UnArchiveButton"

export const ArchiveToggleButton = () => {
  const archived = useSelector(isArchived)
  return (
    archived ? <UnArchiveButton/> : <ArchiveButton/>
  )
}
