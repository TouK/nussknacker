import {css} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {SetStateAction, useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {editNode, nodeValidationDataClear, validateNodeData} from "../../../../actions/nk"
import {visualizationUrl} from "../../../../common/VisualizationUrl"
import {alpha, tint, useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {Edge, NodeType, Process} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {replaceWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"
import urljoin from "url-join"
import {BASE_PATH} from "../../../../config"
import {RootState} from "../../../../reducers"
import {applyIdFromFakeName} from "../IdField"
import LoaderSpinner from "../../../Spinner"
import {getHasValidationResult, getProcessId} from "../NodeDetailsContent/selectors"

interface NodeDetailsProps extends WindowContentProps<WindowKind, { node: NodeType, process: Process }> {
  readOnly?: boolean,
}

export function NodeDetails(props: NodeDetailsProps): JSX.Element {
  const defaultProcess = useSelector(getProcessToDisplay)
  const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly))

  const {node: originalNode, process = defaultProcess} = props.data.meta
  const [editedNode, setEditedNode] = useState<NodeType>(originalNode)
  const [outputEdges, setOutputEdges] = useState(() => process.edges.filter(({from}) => from === originalNode.id))

  useEffect(() => {
    setEditedNode(originalNode)
  }, [originalNode])

  const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
    setEditedNode(node)
    setOutputEdges(edges)
  }, [])

  const dispatch = useDispatch()

  useEffect(() => {
    replaceWindowsQueryParams({nodeId: originalNode.id})
    return () => replaceWindowsQueryParams({}, {nodeId: originalNode.id})
  }, [originalNode.id])

  const performNodeEdit = useCallback(async () => {
    await dispatch(editNode(process, originalNode, applyIdFromFakeName(editedNode), outputEdges))
    props.close()
  }, [process, originalNode, editedNode, outputEdges, dispatch, props])

  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
        disabled: !editedNode?.id?.length,
        classname: css({
          //increase (x4) specificity over ladda
          "&&&&": {
            backgroundColor: theme.colors.accent,
            ":hover": {
              backgroundColor: tint(theme.colors.accent, .25),
            },
            "&[disabled], &[data-loading]": {
              "&, &:hover": {
                backgroundColor: alpha(theme.colors.accent, .5),
              },
            },
          },
        }),
      } :
      null,
    [editedNode?.id?.length, performNodeEdit, readOnly, t, theme.colors.accent],
  )

  const openSubprocessButtonData: WindowButtonProps | null = useMemo(
    () => NodeUtils.nodeIsSubprocess(originalNode) ?
      {
        title: t("dialog.button.fragment.edit", "edit fragment"),
        action: () => {
          window.open(urljoin(BASE_PATH, visualizationUrl(originalNode.ref.id)))
        },
      } :
      null
    ,
    [originalNode, t],
  )

  const cancelButtonData = useMemo(
    () => ({title: t("dialog.button.cancel", "cancel"), action: () => props.close()}),
    [props, t],
  )

  const buttons: WindowButtonProps[] = useMemo(
    () => [openSubprocessButtonData, cancelButtonData, applyButtonData].filter(Boolean),
    [applyButtonData, cancelButtonData, openSubprocessButtonData],
  )

  const components = useMemo(() => {
    const HeaderTitle = () => <NodeDetailsModalHeader node={originalNode}/>
    return {HeaderTitle}
  }, [originalNode])

  const processId = useSelector(getProcessId)

  useEffect(() => {
    return () => {
      dispatch(nodeValidationDataClear(originalNode.id))
    }
  }, [dispatch, originalNode.id])

  useEffect(() => {
    if (!editedNode) return
    dispatch(validateNodeData(processId, {
      outgoingEdges: outputEdges,
      nodeData: editedNode,
    }))
  }, [dispatch, editedNode, outputEdges, processId])

  const hasValidationResult = useSelector(getHasValidationResult)
  return hasValidationResult(editedNode?.id) ?
    (
      <WindowContent
        {...props}
        buttons={buttons}
        components={components}
        classnames={{
          content: css({minHeight: "100%", display: "flex", ">div": {flex: 1}}),
        }}
      >
        <ErrorBoundary>
          <NodeGroupContent
            node={editedNode}
            edges={outputEdges}
            onChange={!readOnly && onChange}
          />
        </ErrorBoundary>
      </WindowContent>
    ) :
    (
      <LoaderSpinner show/>
    )
}

export default NodeDetails
