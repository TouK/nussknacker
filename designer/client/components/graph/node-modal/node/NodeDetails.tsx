import {css} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {SetStateAction, useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {editNode} from "../../../../actions/nk"
import {setAndPreserveLocationParams, visualizationUrl} from "../../../../common/VisualizationUrl"
import {alpha, tint, useNkTheme} from "../../../../containers/theme"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {Edge, NodeType, Process} from "../../../../types"
import {WindowContent, WindowKind} from "../../../../windowManager"
import {parseWindowsQueryParams} from "../../../../windowManager/useWindows"
import ErrorBoundary from "../../../common/ErrorBoundary"
import NodeUtils from "../../NodeUtils"
import NodeDetailsModalHeader from "../NodeDetailsModalHeader"
import {NodeGroupContent} from "./NodeGroupContent"
import {getReadOnly} from "./selectors"
import urljoin from "url-join"
import {BASE_PATH} from "../../../../config"
import {RootState} from "../../../../reducers"
import {applyIdFromFakeName} from "../IdField"
import {mapValues} from "lodash"
import {ensureArray} from "../../../../common/arrayUtils"
import {useNavigate} from "react-router-dom"

interface NodeDetailsProps extends WindowContentProps<WindowKind, { node: NodeType, process: Process }> {
  readOnly?: boolean,
}

export function NodeDetails(props: NodeDetailsProps): JSX.Element {
  const processFromGlobalStore = useSelector(getProcessToDisplay)
  const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly))

  const {node, process = processFromGlobalStore} = props.data.meta
  const [editedNode, setEditedNode] = useState<NodeType>(node)
  const [outputEdges, setOutputEdges] = useState(() => process.edges.filter(({from}) => from === node.id))

  const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
    setEditedNode(node)
    setOutputEdges(edges)
  }, [])

  const dispatch = useDispatch()

  const navigate = useNavigate()
  const replaceWindowsQueryParams = useCallback(
    <P extends Record<string, string | string[]>>(add: P, remove?: P): void => {
      const params = parseWindowsQueryParams(add, remove)
      const search = setAndPreserveLocationParams(mapValues(params, v => ensureArray(v).map(encodeURIComponent)))
      navigate({search}, {replace: true})
    },
    [navigate]
  )

  useEffect(() => {
    replaceWindowsQueryParams({nodeId: node.id})
    return () => replaceWindowsQueryParams({}, {nodeId: node.id})
  }, [node.id])

  const performNodeEdit = useCallback(async () => {
    await dispatch(editNode(process, node, applyIdFromFakeName(editedNode), outputEdges))
    props.close()
  }, [process, node, editedNode, outputEdges, dispatch, props])

  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const applyButtonData: WindowButtonProps | null = useMemo(
    () => !readOnly ?
      {
        title: t("dialog.button.apply", "apply"),
        action: () => performNodeEdit(),
        disabled: !editedNode.id?.length,
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
    [editedNode.id?.length, performNodeEdit, readOnly, t, theme.colors.accent],
  )

  const openSubprocessButtonData: WindowButtonProps | null = useMemo(
    () => NodeUtils.nodeIsSubprocess(editedNode) ?
      {
        title: t("dialog.button.fragment.edit", "edit fragment"),
        action: () => {
          window.open(urljoin(BASE_PATH, visualizationUrl(editedNode.ref.id)))
        },
      } :
      null
    ,
    [editedNode, t],
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
    const HeaderTitle = () => <NodeDetailsModalHeader node={node}/>
    return {HeaderTitle}
  }, [node])

  //no process? no nodes? no window contents! no errors for whole tree!
  if (!processFromGlobalStore?.nodes) {
    return null
  }

  return (
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
  )
}

export default NodeDetails
