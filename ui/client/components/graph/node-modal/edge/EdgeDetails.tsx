import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {closeModals, editEdge} from "../../../../actions/nk"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {Edge} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {ContentWrapper} from "../node/ContentWrapper"
import {Details} from "./Details"
import {EdgeDetailsModalHeader} from "./EdgeDetailsModalHeader"

export function EdgeDetails(props: WindowContentProps<WindowKind, Edge>): JSX.Element {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const processToDisplay = useSelector(getProcessToDisplay)
  const [edge, setEdge] = useState(props.data.meta)

  useEffect(() => () => {
    dispatch(closeModals())
  }, [dispatch])

  const performEdgeEdit = useCallback(async () => {
    await dispatch(editEdge(processToDisplay, props.data.meta, edge))
    props.close()
  }, [dispatch, edge, processToDisplay, props])

  const cancelButtonData = useMemo(
    () => ({title: t("dialog.button.cancel", "cancel"), action: () => props.close()}),
    [props, t],
  )

  const readOnly = false
  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performEdgeEdit(),
      } :
      null,
    [performEdgeEdit, readOnly, t],
  )

  const buttons: WindowButtonProps[] = useMemo(
    () => [cancelButtonData, applyButtonData].filter(Boolean),
    [applyButtonData, cancelButtonData],
  )

  const components = useMemo(() => ({HeaderTitle: EdgeDetailsModalHeader}), [])
  return (
    <WindowContent
      {...props}
      buttons={buttons}
      components={components}
    >
      <ContentWrapper>
        <Details edge={edge} onChange={setEdge} processToDisplay={processToDisplay}/>
      </ContentWrapper>
    </WindowContent>
  )
}
