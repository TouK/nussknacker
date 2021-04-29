import classnames from "classnames/dedupe"
import i18next from "i18next"
import {debounce, flatMap, uniq} from "lodash"
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

const CLASSNAME = "tokenizer-working"

function useAliasUsageHighlight(token = "alias") {
  const [keywords, setKeywords] = useState<string>("")
  const ref = useRef<ReactAce>()
  const editor = ref.current?.editor
  const session = useMemo(() => editor?.getSession(), [editor])

  const getValuesForToken = useCallback(
    (line: string, index: number) => session?.getTokens(index)
      .filter(({type}) => type === token)
      .map(({value}) => value.trim().toLowerCase()),
    [session, token],
  )

  const toggleClassname = useCallback(debounce((classname: string, enabled: boolean): void => {
    const el = ref.current.refEditor
    el.className = classnames(el.className, {[classname]: enabled})
  }, 1000, {trailing: true, leading: true}), [])

  useEffect(() => {
    if (session?.getMode().$highlightRules.setAliases) {
      // for cypress tests only, we need some "still working" state
      toggleClassname(CLASSNAME, true)
      session.bgTokenizer.stop()
      session.getMode().$highlightRules.setAliases(keywords)
      session.bgTokenizer.start(0)
    }
  }, [toggleClassname, session, keywords])

  useEffect(() => {
    const callback = () => {
      const allLines = session.bgTokenizer.lines
      const next = uniq(flatMap(allLines, getValuesForToken)).join("|")
      setKeywords(next)
      toggleClassname(CLASSNAME, false)
    }
    session?.on(`tokenizerUpdate`, callback)
    return () => {
      session?.off(`tokenizerUpdate`, callback)
    }
  }, [toggleClassname, session, getValuesForToken])

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
