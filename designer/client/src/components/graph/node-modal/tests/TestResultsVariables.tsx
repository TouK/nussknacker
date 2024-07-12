import { FormControl, FormLabel } from "@mui/material";
import TestValue from "./TestValue";
import React, { useRef, useState } from "react";
import { HIDDEN_TEXTAREA_PIXEL_HEIGHT } from "../NodeDetailsContent/NodeTableStyled";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import { Variable } from "../../../../common/TestResultUtils";

interface TestResultsVariablesProps {
    labelText: string;
    result: Variable;
}

export default function TestResultsVariables(props: TestResultsVariablesProps): JSX.Element {
    const { labelText, result } = props;
    const testValueRef: React.Ref<HTMLTextAreaElement> = useRef(null);
    const fitsMaxHeight = testValueRef?.current ? testValueRef.current.scrollHeight <= HIDDEN_TEXTAREA_PIXEL_HEIGHT : true;
    const [collapsedTestResults, setCollapsedTestResults] = useState(true);
    const PrettyIconComponent = collapsedTestResults ? VisibilityOff : Visibility;

    return (
        <FormControl>
            <FormLabel sx={{ flexDirection: "column" }}>
                {labelText}:
                {!fitsMaxHeight ? (
                    <FormLabel>
                        <PrettyIconComponent sx={{ cursor: "pointer" }} onClick={() => setCollapsedTestResults((s) => !s)} />
                    </FormLabel>
                ) : null}
            </FormLabel>
            <TestValue ref={testValueRef} value={result} shouldHideTestResults={collapsedTestResults && !fitsMaxHeight} />
        </FormControl>
    );
}
