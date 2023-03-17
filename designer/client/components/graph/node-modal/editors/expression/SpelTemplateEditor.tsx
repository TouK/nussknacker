import React, {useMemo} from "react"
import RawEditor, {RawEditorProps} from "./RawEditor"
import {ExpressionLang} from "./types";

const SpelTemplateEditor = (props: RawEditorProps) => {

  const {expressionObj, ...passProps} = props

  const value = useMemo(() => ({
    expression: expressionObj.expression,
    language: ExpressionLang.SpEL,
  }), [expressionObj])

  return (
    <RawEditor
      {...passProps}
      expressionObj={value}
      rows={6}
    />
  )
}

export default SpelTemplateEditor
