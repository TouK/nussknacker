import React, { useEffect, useState } from "react";
import { ExpressionObj } from "../types";
import { useDebouncedCallback } from "use-debounce";
import moment from "moment";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { Formatter } from "../Formatter";
import { DTPicker } from "../../../../../common/DTPicker";
import { cx } from "@emotion/css";
import { FieldError } from "../../Validators";
import { isEmpty } from "lodash";
import { nodeInput, nodeInputWithError } from "../../../NodeDetailsContent/NodeTableStyled";

export interface DatepickerEditorProps {
    expressionObj: ExpressionObj;
    readOnly: boolean;
    className: string;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    showValidation: boolean;
    isMarked: boolean;
    editorFocused: boolean;
    formatter: Formatter;
    momentFormat: string;
    dateFormat?: string;
    timeFormat?: string;
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
    const [onChange] = useDebouncedCallback<[value: string | moment.Moment]>((value) => {
        const encoded = encode(value);
        onValueChange(encoded);
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
