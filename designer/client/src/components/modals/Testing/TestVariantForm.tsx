import type { WindowType } from "@touk/window-manager";
import React, { useMemo } from "react";
import { useErrorBoundary } from "react-error-boundary";
import { useSelector } from "react-redux";

import { getTestParameters } from "../../../reducers/selectors/graph";
import type { WindowKind } from "../../../windowManager";
import type { TestingData } from "./TestingDialog";
import { TestType } from "./TestingForm";
import { TestWithGeneratedDataForm } from "./TestWithGeneratedData";
import { TestWithParametersMultipleSourcesForm } from "./TestWithParametersMultipleSourcesForm";
import { TestWithParametersSingleSourceForm } from "./TestWithParametersSingleSourceForm";

interface TestVariantFormProps {
    testType: string;
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

export function TestVariantForm({ testType, testingData, closeDialog }: TestVariantFormProps): JSX.Element {
    const testParameters = useSelector(getTestParameters);
    const sourcesFound = testParameters.length;
    const { showBoundary } = useErrorBoundary();

    return useMemo(() => {
        switch (testType) {
            case TestType.withParameters:
                if (sourcesFound > 1) {
                    return <TestWithParametersMultipleSourcesForm numberOfSources={sourcesFound}></TestWithParametersMultipleSourcesForm>;
                } else {
                    return (
                        <TestWithParametersSingleSourceForm
                            testingData={testingData}
                            closeDialog={closeDialog}
                        ></TestWithParametersSingleSourceForm>
                    );
                }
            case TestType.withGeneratedData:
                return <TestWithGeneratedDataForm closeDialog={closeDialog}></TestWithGeneratedDataForm>;
            default:
                showBoundary(`There is no form available for test type ${testType}`);
        }
    }, [testType, sourcesFound, closeDialog, testingData, showBoundary]);
}
