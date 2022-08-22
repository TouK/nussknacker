import React, {useEffect, useState} from "react"
import HttpService from "../../../http/HttpService"
import ReactMarkdown from "react-markdown/with-html"
import "../../../stylesheets/markdown.styl"
import {useDebounce} from "use-debounce"
import {NodeType} from "../../../types"
import {useSelector} from "react-redux"
import {getProcessId} from "./NodeDetailsContent/selectors"

interface Props {
  node: NodeType,
}

//Types should match implementations of NodeAdditionalInfo on Backend!
export type NodeAdditionalInfo = MarkdownNodeAdditionalInfo

interface MarkdownNodeAdditionalInfo {
  type: "MarkdownNodeAdditionalInfo",
  content: string,
}

export default function NodeAdditionalInfoBox(props: Props): JSX.Element {
  const {node} = props
  const processId = useSelector(getProcessId)

  const [additionalData, setAdditionalData] = useState<NodeAdditionalInfo>(null)

  //We don't use redux here since this additionalData is local to this component. We use debounce, as
  //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
  const [debouncedNode] = useDebounce(node, 1000)
  useEffect(() => {
    if (debouncedNode?.type && processId) {
      HttpService.getNodeAdditionalData(processId, debouncedNode).then(res => setAdditionalData(res.data))
    }
  }, [processId, debouncedNode])

  if (!additionalData?.type) {
    return null
  }

  switch (additionalData.type) {
    case "MarkdownNodeAdditionalInfo":
      // eslint-disable-next-line i18next/no-literal-string
      const linkTarget = "_blank"
      return <ReactMarkdown source={additionalData.content} className="markdownDisplay" linkTarget={linkTarget}/>
    default:
      // eslint-disable-next-line i18next/no-literal-string
      console.warn("Unknown type:", additionalData.type)
      return null
  }
}
