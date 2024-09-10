import { ExpressionObj } from "../types";
import React, { useEffect, useRef, useState } from "react";
import Cron from "react-cron-generator";
import "react-cron-generator/dist/cron-builder.css";
import Input from "../../field/Input";
import i18next from "i18next";
import { Formatter, FormatterType, spelFormatters, typeFormatters } from "../Formatter";
import { CronEditorStyled } from "./CronEditorStyled";
import { ExtendedEditor } from "../Editor";
import { FieldError } from "../../Validators";
import { nodeValue } from "../../../NodeDetailsContent/NodeTableStyled";

export type CronExpression = string;

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    showValidation: boolean;
    readOnly: boolean;
    isMarked: boolean;
    formatter: Formatter;
};

// we have to pass some value to <Cron/> component
// when expression is empty - this component sets some default cron value and trigger onValueChange - we don't want that
const NOT_EXISTING_CRON_EXPRESSION = "-1 -1 -1 -1 -1 -1 -1";

export const CronEditor: ExtendedEditor<Props> = (props: Props) => {
    const node = useRef(null);

    const { expressionObj, isMarked, onValueChange, showValidation, readOnly, formatter, fieldErrors } = props;

    const cronFormatter = formatter == null ? typeFormatters[FormatterType.Cron] : formatter;

    function encode(value) {
        return value == "" ? "" : cronFormatter.encode(value);
    }

    function decode(expression: string): CronExpression {
        const result = cronFormatter.decode(expression);
        return result == null || typeof result !== "string" ? "" : result;
    }

    const [value, setValue] = useState(decode(expressionObj.expression));
    const [open, setOpen] = useState(false);

    const handleClickOutside = (e) => {
        if (node.current.contains(e.target)) {
            return;
        }
        setOpen(false);
    };

    useEffect(() => {
        if (open) {
            document.addEventListener("mousedown", handleClickOutside);
        } else {
            document.removeEventListener("mousedown", handleClickOutside);
        }
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, [open]);

    useEffect(() => {
        onValueChange(encode(value));
    }, [value]);

    const onInputFocus = () => {
        if (!readOnly) {
            setOpen(true);
        }
    };

    return (
        <CronEditorStyled ref={node} className={nodeValue}>
            <Input
                value={value}
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                onFocus={onInputFocus}
                showValidation={showValidation}
                readOnly={readOnly}
                inputClassName={readOnly ? "read-only" : ""}
            />
            {open && (
                <Cron
                    onChange={(e) => {
                        setValue(e);
                    }}
                    value={value === "" ? NOT_EXISTING_CRON_EXPRESSION : value}
                    showResultText={true}
                    showResultCron={false}
                />
            )}
        </CronEditorStyled>
    );
};

CronEditor.isSwitchableTo = (expressionObj: ExpressionObj) =>
    spelFormatters[FormatterType.Cron].decode(expressionObj.expression) != null || expressionObj.expression === "";

CronEditor.switchableToHint = () => i18next.t("editors.cron.switchableToHint", "Switch to basic mode");

CronEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.cron.notSwitchableToHint",
        "Expression must match pattern new com.cronutils.parser.CronParser(T(com.cronutils.model.definition.CronDefinitionBuilder).instanceDefinitionFor(T(com.cronutils.model.CronType).QUARTZ)).parse('* * * * * * *') to switch to basic mode",
    );
