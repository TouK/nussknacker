import React, {useEffect, useState} from "react"
import HttpService from "../../../http/HttpService"
import ReactMarkdown from "react-markdown/with-html"
import "../../../stylesheets/markdown.styl"
import {useDebounce} from "use-debounce"
import {NodeType} from "../../../types"
import {useSelector} from "react-redux"
import {getProcessId} from "./NodeDetailsContent/selectors"
import NodeUtils from "../NodeUtils";

interface Props {
  node: NodeType,
}

//Types should match implementations of AdditionalInfo on Backend!
export type AdditionalInfo = MarkdownAdditionalInfo

interface MarkdownAdditionalInfo {
  type: "MarkdownAdditionalInfo",
  content: string,
}

export default function NodeAdditionalInfoBox(props: Props): JSX.Element {
  const {node} = props
  const processId = useSelector(getProcessId)

  const [additionalInfo, setAdditionalInfo] = useState<AdditionalInfo>(null)

  //We don't use redux here since this additionalInfo is local to this component. We use debounce, as
  //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
  const [debouncedNode] = useDebounce(node, 1000)
  useEffect(() => {
    if (processId) {
      const nodeType = NodeUtils.nodeType(debouncedNode)
      if(nodeType === "Properties") {
        HttpService.getPropertiesAdditionalInfo(processId, debouncedNode).then(res => setAdditionalInfo(res.data))
      } else {
        HttpService.getNodeAdditionalInfo(processId, debouncedNode).then(res => setAdditionalInfo(res.data))
      }
    }
  }, [processId, debouncedNode])

  if (!additionalInfo?.type) {
    return null
  }

  switch (additionalInfo.type) {
    case "MarkdownAdditionalInfo":
      // eslint-disable-next-line i18next/no-literal-string
      const linkTarget = "_blank"
      return <ReactMarkdown className="markdownDisplay" linkTarget={linkTarget}>{additionalInfo.content}</ReactMarkdown>
    default:
      // eslint-disable-next-line i18next/no-literal-string
      console.warn("Unknown type:", additionalInfo.type)
      return null
  }
}
