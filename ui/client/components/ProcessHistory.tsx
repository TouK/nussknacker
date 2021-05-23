import React, {useCallback, useMemo, useState} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {useDispatch, useSelector} from "react-redux"
import {fetchProcessToDisplay, toggleConfirmDialog} from "../actions/nk"
import {unsavedProcessChanges} from "../common/DialogMessages"
import {getFetchedProcessDetails, isBusinessView, isSaveDisabled} from "../reducers/selectors/graph"
import styles from "../stylesheets/processHistory.styl"
import {HistoryItem, VersionType} from "./HistoryItem"
import {ProcessVersionType} from "./Process/types"

export function ProcessHistoryComponent(props: {isReadOnly?: boolean}): JSX.Element {
  const {history = [], lastDeployedAction, name, processVersionId} = useSelector(getFetchedProcessDetails)
  const nothingToSave = useSelector(isSaveDisabled)
  const businessView = useSelector(isBusinessView)
  const selectedVersion = useMemo(
    () => history.find(v => v.processVersionId === processVersionId),
    [history, processVersionId],
  )

  const dispatch = useDispatch()

  const doChangeVersion = useCallback((version: ProcessVersionType) => {
    dispatch(fetchProcessToDisplay(name, version.processVersionId, businessView))
  }, [dispatch, name, businessView])

  const changeVersion = useCallback(
    (version: ProcessVersionType) => props.isReadOnly || nothingToSave ?
      doChangeVersion(version) :
      dispatch(toggleConfirmDialog(true, unsavedProcessChanges(), () => doChangeVersion(version), "DISCARD", "NO", null)),
    [dispatch, doChangeVersion, nothingToSave, props.isReadOnly],
  )

  return (
    <Scrollbars
      renderTrackVertical={(props) => <div {...props} className={styles.innerScroll}/>}
      renderTrackHorizontal={() => <div className="hide"/>}
      autoHeight
      autoHeightMax={300}
      hideTracksWhenNotNeeded={true}
    >
      <ul id="process-history">
        {history.map((version, index) => {
          const isLatest = index === 0
          const {createDate, processVersionId} = version
          const type = selectedVersion?.createDate < createDate ?
            VersionType.future :
            selectedVersion?.createDate === createDate || isLatest ?
              VersionType.current :
              VersionType.past

          return (
            <HistoryItem
              key={processVersionId}
              isLatest={isLatest}
              isDeployed={processVersionId === lastDeployedAction?.processVersionId}
              version={version}
              type={type}
              onClick={changeVersion}
            />
          )
        })}
      </ul>
    </Scrollbars>
  )
}

export default ProcessHistoryComponent
