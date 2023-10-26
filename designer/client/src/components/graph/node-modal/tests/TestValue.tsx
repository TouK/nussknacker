import React from "react";
import { Variable } from "../../../../common/TestResultUtils";
import { cx } from "@emotion/css";

interface Props {
    value: Variable;
    shouldHideTestResults?: boolean;
}

function prettyPrint(obj: unknown) {
    return JSON.stringify(obj, null, 2);
}

export default function TestValue(props: Props) {
    const { value, shouldHideTestResults } = props;

    return (
        <div className={cx("node-value", shouldHideTestResults && "partly-hidden")}>
            {value?.original ? <ReadonlyTextarea value={value.original} /> : null}
            <ReadonlyTextarea value={prettyPrint(value?.pretty)} />
            {shouldHideTestResults ? <div className="fadeout" /> : null}
        </div>
    );
}

function ReadonlyTextarea({ value = "" }: { value: string }) {
    return <textarea className="node-input" readOnly value={value} rows={value.split("\n").length} />;
}
