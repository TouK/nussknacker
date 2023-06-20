import React from "react";
import Textarea from "react-textarea-autosize";
import { get } from "lodash";
import { Variable } from "../../../../common/TestResultUtils";

interface Props {
    value: Variable;
    shouldHideTestResults?: boolean;
}

export default function TestValue(props: Props) {
    const { value, shouldHideTestResults } = props;
    const hiddenClassPart = shouldHideTestResults ? " partly-hidden" : "";

    return (
        <div className={`node-value${hiddenClassPart}`}>
            {get(value, "original") ? <Textarea className="node-input" readOnly={true} value={value.original} /> : null}
            <Textarea className="node-input" readOnly={true} value={value !== null ? prettyPrint(value.pretty) : "null"} />
            {shouldHideTestResults ? <div className="fadeout" /> : null}
        </div>
    );

    function prettyPrint(obj: unknown) {
        return JSON.stringify(obj, null, 2);
    }
}
