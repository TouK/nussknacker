import type { WindowType } from "@touk/window-manager";
import React from "react";
import { useSelector } from "react-redux";

import { getTestParameters } from "../../../reducers/selectors/graph";
import type { WindowKind } from "../../../windowManager";
import type { TestingData } from "./TestingDialog";
import { TestWithLiveDataForm } from "./TestWithLiveData";
import { TestWithParametersMultipleSourcesForm } from "./TestWithParametersMultipleSourcesForm";
import { TestWithParametersSingleSourceForm } from "./TestWithParametersSingleSourceForm";
import { TestType } from "./useTestOptions";

interface TestVariantFormProps {
    testType: string;
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

export function TestVariantForm({ testType, testingData, closeDialog }: TestVariantFormProps): JSX.Element {
    const testParameters = useSelector(getTestParameters);
    const sourcesFound = testParameters.length;

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
        case TestType.withLiveData:
            return <TestWithLiveDataForm closeDialog={closeDialog}></TestWithLiveDataForm>;
        default:
            throw `There is no form available for test type ${testType}`;
    }
}
