import { cx } from "@emotion/css";
import { isEmpty } from "lodash";
import moment from "moment";
import React, { useEffect, useState } from "react";
import { useDebounceFn } from "rooks";

import { DTPicker } from "../../../../../common/DTPicker";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { nodeInput, nodeInputWithError } from "../../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../../Validators";
import type { OnValueChange } from "../Editor";
import type { Formatter } from "../Formatter";
import type { ExpressionLang, ExpressionObj } from "../types";

export interface DatepickerEditorProps {
    expressionObj: ExpressionObj;
    readOnly?: boolean;
    className: string;
    onValueChange: OnValueChange;
    fieldErrors: FieldError[];
    showValidation?: boolean;
    isMarked: boolean;
    editorFocused?: boolean;
    formatter: Formatter;
    momentFormat: string;
    dateFormat?: string;
    timeFormat?: string;
    language?: ExpressionLang;
}

export function DatepickerEditor(props: DatepickerEditorProps) {
    const {
        className,
        expressionObj,
        onValueChange,
        readOnly,
        fieldErrors,
        showValidation,
        isMarked,
        editorFocused,
        formatter,
        momentFormat,
        language,
        ...other
    } = props;

    function encode(value: string | moment.Moment): string {
        const m = moment(value, momentFormat);
        if (m.isValid()) {
            return formatter.encode(m);
        }
        return "";
    }

    const decode = (expression): moment.Moment | null => {
        const date = formatter.decode(expression);
        const m = moment(date, momentFormat);
        return m.isValid() ? m : null;
    };

    const { expression } = expressionObj;
    const [value, setValue] = useState<string | moment.Moment>(decode(expression) == null ? null : decode(expression));
    const [onChange] = useDebounceFn((value: string | moment.Moment) => {
        const encoded = encode(value);
        onValueChange({ expression: encoded, language });
    }, 200);

    useEffect(() => {
        onChange(value);
    }, [onChange, value]);

    return (
        <div className={className}>
            <DTPicker
                onChange={setValue}
                value={value}
                inputProps={{
                    className: cx([
                        nodeInput,
                        showValidation && !isEmpty(fieldErrors) && nodeInputWithError,
                        isMarked && "marked",
                        editorFocused && "focused",
                        readOnly && "read-only",
                    ]),
                    readOnly,
                    disabled: readOnly,
                }}
                {...other}
            />
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
}
