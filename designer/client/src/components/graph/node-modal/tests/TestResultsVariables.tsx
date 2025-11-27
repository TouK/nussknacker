import { Visibility, VisibilityOff } from "@mui/icons-material";
import React, { useRef, useState } from "react";

import type { Variable } from "../../../../http/resultsWithCountsDto";
import { FormControl, FormLabel } from "../editors/FormControl";
import { HIDDEN_TEXTAREA_PIXEL_HEIGHT } from "../NodeDetailsContent/NodeTableStyled";
import TestValue from "./TestValue";

interface TestResultsVariablesProps {
    labelText: string;
    result: Variable;
}

export default function TestResultsVariables(props: TestResultsVariablesProps): React.JSX.Element {
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
