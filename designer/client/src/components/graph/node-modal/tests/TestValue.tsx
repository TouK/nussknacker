import React, { forwardRef, useEffect, useImperativeHandle, useRef } from "react";
import { Variable } from "../../../../common/TestResultUtils";
import { cx } from "@emotion/css";

interface Props {
    value: Variable;
    shouldHideTestResults?: boolean;
}

function prettyPrint(obj: unknown) {
    return JSON.stringify(obj, null, 2);
}

export default forwardRef<HTMLTextAreaElement, Props>(function TestValue(props: Props, ref: React.Ref<HTMLTextAreaElement>) {
    const { value, shouldHideTestResults } = props;
    return (
        <div className={cx("node-value", shouldHideTestResults && "partly-hidden")}>
            {value?.original ? <ReadonlyTextarea ref={ref} value={value.original} /> : null}
            <ReadonlyTextarea ref={ref} value={prettyPrint(value?.pretty)} />
            {shouldHideTestResults ? <div className="fadeout" /> : null}
        </div>
    );
});

const ReadonlyTextarea = forwardRef<HTMLTextAreaElement, { value: string }>(function ReadonlyTextarea(
    { value = "" }: { value: string },
    outerRef: React.Ref<HTMLTextAreaElement>,
) {
    const innerRef = useRef<HTMLTextAreaElement>(null);
    useImperativeHandle(outerRef, () => innerRef.current, []);
    useEffect(() => {
        innerRef.current.style.height = innerRef.current.scrollHeight + "px";
    }, []);

    return <textarea ref={innerRef} className="node-input" readOnly value={value} rows={value.split("\n").length} />;
});
