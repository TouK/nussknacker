import { css, cx } from "@emotion/css";
import type { WindowType } from "@touk/window-manager";
import React from "react";
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
    const thereAreMultipleSources = sourcesFound > 1;

    const testWithParametersElementWhenSingleSource =
        testType === TestType.withParameters && !thereAreMultipleSources ? (
            <TestWithParametersSingleSourceForm testingData={testingData} closeDialog={closeDialog}></TestWithParametersSingleSourceForm>
        ) : (
            <></>
        );

    const testWithParametersElementWhenMultipleSources =
        testType === TestType.withParameters && thereAreMultipleSources ? (
            <TestWithParametersMultipleSourcesForm numberOfSources={sourcesFound}></TestWithParametersMultipleSourcesForm>
        ) : (
            <></>
        );

    const testWithGeneratedDataElement =
        testType === TestType.withGeneratedData ? <TestWithGeneratedDataForm closeDialog={closeDialog}></TestWithGeneratedDataForm> : <></>;

    return (
        <div className={cx(css({ paddingTop: 10, paddingBottom: 20 }))}>
            {testWithParametersElementWhenSingleSource}
            {testWithParametersElementWhenMultipleSources}
            {testWithGeneratedDataElement}
        </div>
    );
}
