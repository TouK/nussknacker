import React, {useState, useEffect} from "react"
import HttpService from "../../../http/HttpService"
import ReactMarkdown from "react-markdown/with-html"
import "../../../stylesheets/markdown.styl"
import {useDebounce} from "use-debounce"

type Props = {
  processId: string,
  node: any,
}

//Types should match implementations of NodeAdditionalInfo on Backend!
type NodeAdditionalInfo = MarkdownNodeAdditionalInfo

type MarkdownNodeAdditionalInfo = {
    type: "MarkdownNodeAdditionalInfo",
    content: string,
}

export default function NodeAdditionalInfoBox(props: Props) {

  const [additionalData, setAdditionalData] = useState<NodeAdditionalInfo>(null)
  const {
    processId, node,
  } = props

  //We don't use redux here since this additionalData is local to this component. We use debounce, as
  //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
  const [debouncedNode] = useDebounce(node, 1000)
  useEffect(() => {
    if (node?.type) {
      HttpService.getNodeAdditionalData(processId, debouncedNode).then(res => setAdditionalData(res.data))
    }
  }, [processId, debouncedNode])

  const type = additionalData?.type
  if (!type) {
    return null
  }
  switch (type){
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
