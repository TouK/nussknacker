import i18next from "i18next"
import React, {useCallback, useEffect, useMemo} from "react"
import {UnknownFunction} from "../../../../../types/common"
import {SimpleEditor} from "./Editor"
import {Formatter, FormatterType, typeFormatters} from "./Formatter"
import {getQuotedStringPattern, QuotationMark} from "./SpelQuotesUtils"
import RawEditor from "./RawEditor"
import {ExpressionObj} from "./types"

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: UnknownFunction,
  className: string,
  formatter: Formatter,
}

const SqlEditor: SimpleEditor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className, formatter} = props
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
    language: "sql",
  }), [sqlFormatter, expressionObj])

  useEffect(() => {
    valueChange(value.expression)
  }, [])

  return (
    <RawEditor
      onValueChange={valueChange}
      expressionObj={value}
      className={className}
    />
  )
}

const quotedStringPattern = getQuotedStringPattern([QuotationMark.single, QuotationMark.double])

const parseable = ({expression, language}: ExpressionObj) => {
  return language === "spel" && quotedStringPattern.test(expression.trim())
}

SqlEditor.switchableTo = (expressionObj) => parseable(expressionObj)
SqlEditor.switchableToHint = () => i18next.t("editors.textarea.switchableToHint", "Switch to basic mode")
SqlEditor.notSwitchableToHint = () => i18next.t(
  "editors.textarea.notSwitchableToHint",
  "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode",
)

export default SqlEditor
