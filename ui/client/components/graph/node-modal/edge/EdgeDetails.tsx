import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import {css} from "emotion"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {editEdge} from "../../../../actions/nk"
import {useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {Edge} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {replaceWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import {ContentSize} from "../node/ContentSize"
import {Details} from "./Details"
import {EdgeDetailsModalHeader} from "./EdgeDetailsModalHeader"

export function EdgeDetails(props: WindowContentProps<WindowKind, Edge>): JSX.Element {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const processToDisplay = useSelector(getProcessToDisplay)
  const edgeToDisplay = props.data.meta
  const [edge, setEdge] = useState(edgeToDisplay)

  useEffect(() => {
    const edgeId = NodeUtils.edgeId(edgeToDisplay)
    replaceWindowsQueryParams({edgeId})
    return () => replaceWindowsQueryParams({}, {edgeId})
  }, [edgeToDisplay])

  const performEdgeEdit = useCallback(async () => {
    await dispatch(editEdge(processToDisplay, edgeToDisplay, edge))
    props.close()
  }, [dispatch, edge, edgeToDisplay, processToDisplay, props])

  const cancelButtonData = useMemo(
    () => ({title: t("dialog.button.cancel", "cancel"), action: () => props.close()}),
    [props, t],
  )

  const {theme} = useNkTheme()

  const readOnly = false
  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performEdgeEdit(),
        classname: css({background: theme.colors.accent}),
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
      <ErrorBoundary>
        <ContentSize>
          <Details edge={edge} onChange={setEdge} processToDisplay={processToDisplay}/>
        </ContentSize>
      </ErrorBoundary>
    </WindowContent>
  )
}
