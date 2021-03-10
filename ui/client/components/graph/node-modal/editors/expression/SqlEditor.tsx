import i18next from "i18next"
import React, {useCallback, useEffect, useMemo} from "react"
import {SimpleEditor} from "./Editor"
import {Formatter, FormatterType, typeFormatters} from "./Formatter"
import {getQuotedStringPattern, QuotationMark} from "./SpelQuotesUtils"
import RawEditor, {RawEditorProps} from "./RawEditor"
import {ExpressionLang, ExpressionObj} from "./types"

interface Props extends RawEditorProps {
  formatter: Formatter,
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

  return (
    <RawEditor
      {...passProps}
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
