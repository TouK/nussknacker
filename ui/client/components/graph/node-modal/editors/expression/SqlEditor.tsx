import i18next from "i18next"
import {flatMap, isEqual, uniq} from "lodash"
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react"
import ReactAce from "react-ace/lib/ace"
import {SimpleEditor} from "./Editor"
import {Formatter, FormatterType, typeFormatters} from "./Formatter"
import RawEditor, {RawEditorProps} from "./RawEditor"
import {getQuotedStringPattern, QuotationMark} from "./SpelQuotesUtils"
import {ExpressionLang, ExpressionObj} from "./types"

interface Props extends RawEditorProps {
  formatter: Formatter,
}

function useAliasUsageHighlight(token = "alias") {
  const [keywords, setKeywords] = useState<string[]>([])
  const ref = useRef<ReactAce>()
  const editor = ref.current?.editor
  const session = useMemo(() => editor?.getSession(), [editor])

  const getValuesForToken = useCallback(
    (line: string, index: number) => session?.getTokens(index)
      .filter(({type}) => type === token)
      .map(({value}) => value.trim().toLowerCase()),
    [session, token],
  )

  useEffect(() => {
    const callback = () => {
      const allLines = session?.bgTokenizer?.lines
      const next = uniq(flatMap(allLines, getValuesForToken))
      setKeywords(current => isEqual(next, current) ? current : next)
    }
    session?.on(`tokenizerUpdate`, callback)
    return () => {
      session?.off(`tokenizerUpdate`, callback)
    }
  }, [session, getValuesForToken])

  const onKeywordsChanged = useCallback(keywords => {
    const tokenizer = session?.bgTokenizer
    tokenizer?.stop()
    session?.getMode()?.$highlightRules.setAliases?.(keywords)
    tokenizer?.start(0)
  }, [session])

  useEffect(() => {
    onKeywordsChanged(keywords)
  }, [onKeywordsChanged, keywords])

  return ref
}

const SqlEditor: SimpleEditor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className, formatter, ...passProps} = props
  const sqlFormatter = formatter == null ? typeFormatters[FormatterType.Sql] : formatter

  const valueChange = useCallback(
    (value: string) => {
      const encoded = sqlFormatter.encode(value.trim())
      if (encoded !== value) {
        return onValueChange(encoded)
      }
    },
    [onValueChange, sqlFormatter],
  )

  const value = useMemo(() => ({
    expression: sqlFormatter.decode(expressionObj.expression.trim()),
    language: ExpressionLang.SQL,
  }), [sqlFormatter, expressionObj])

  useEffect(() => {
    valueChange(value.expression)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const ref = useAliasUsageHighlight()

  return (
    <RawEditor
      {...passProps}
      ref={ref}
      onValueChange={valueChange}
      expressionObj={value}
      className={className}
      rows={6}
    />
  )
}

const quotedStringPattern = getQuotedStringPattern([QuotationMark.single, QuotationMark.double])

const parseable = ({expression, language}: ExpressionObj) => {
  return language === ExpressionLang.SpEL && quotedStringPattern.test(expression.trim())
}

SqlEditor.switchableTo = (expressionObj) => parseable(expressionObj)
SqlEditor.switchableToHint = () => i18next.t("editors.textarea.switchableToHint", "Switch to basic mode")
SqlEditor.notSwitchableToHint = () => i18next.t(
  "editors.textarea.notSwitchableToHint",
  "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode",
)

export default SqlEditor
