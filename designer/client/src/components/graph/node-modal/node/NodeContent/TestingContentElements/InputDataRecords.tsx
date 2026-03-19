import React, { useCallback, useEffect, useMemo, useState } from "react";

import { displayTestCapabilities } from "../../../../../../actions/nk/process";
import type { TestFormParameters } from "../../../../../../common/TestResultUtils";
import { TestCapabilityStatus } from "../../../../../../common/TestResultUtils";
import { getProcessName, getTestCapabilities } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import type { ScenarioGraph } from "../../../../../../types/scenarioGraph";
import { Expandable } from "../../../../../common/Expandable";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { ContentSize } from "../../ContentSize";
import { StyledStack } from "./components/Styled";

interface Props {
    node: NodeType;
    sourceId: string;
    scenarioGraph: ScenarioGraph;
}

export const InputDataRecords = ({ node, sourceId, scenarioGraph }: Props) => {
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, sourceId));

    const {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        generateTestDataForSingleSource,
    } = useDataRecordsActions();

    const dispatch = useAppDispatch();
    const scenarioName = useAppSelector(getProcessName);

    useEffect(() => {
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, scenarioName, scenarioGraph]);

    const testCapabilities = useAppSelector(getTestCapabilities);
    const scenarioProperties = useAppSelector(getProcessProperties);

    const testCapabilitiesParameters = testCapabilities.testWithParameters;

    const defaultParameter: TestFormParameters | undefined =
        testCapabilitiesParameters.status === TestCapabilityStatus.AVAILABLE
            ? testCapabilitiesParameters.sourceParameters.find((sourceParameter) => sourceParameter.sourceId === sourceId)
            : undefined;

    const defaultDataRecord = useMemo(
        () =>
            defaultParameter
                ? {
                      sourceId: defaultParameter.sourceId,
                      timestamp: undefined,
                      variables: defaultParameter.parameters?.[0]?.defaultValue?.expression ?? "",
                  }
                : { sourceId: undefined, timestamp: undefined, variables: undefined },
        [defaultParameter],
    );

    const recordsToAddLimitExceeded = useMemo(
        () => recordsErrors.some((recordsErrors) => recordsErrors.type === "TEST_DATA_LIMIT_EXCEEDED"),
        [recordsErrors],
    );

    const handleGenerateTestDataForSingleSource = useCallback(
        async (numberOfSamples: number) => {
            await generateTestDataForSingleSource(numberOfSamples, scenarioProperties, node);
        },
        [generateTestDataForSingleSource, node, scenarioProperties],
    );

    return (
        <StyledStack>
            <Expandable componentId={"inputDataRecords"} expandableTitle={"Test data"} expanded={isExpanded} onChange={setIsExpanded}>
                <ContentSize sx={{ padding: 0, maxHeight: "45cqh", mb: 2 }}>
                    <Table
                        cellErrors={cellErrors}
                        defaultDataRecord={defaultDataRecord}
                        onRowAdded={handleRowAdded}
                        onRowMoved={handleRowMoved}
                        onRowsDeleted={handleRowsDeleted}
                        onRowUpdated={handleRowUpdated}
                        data={testingDataRecordsForSource}
                        sourceOptions={[sourceId]}
                        sourceParameters={
                            testCapabilitiesParameters.status === TestCapabilityStatus.AVAILABLE
                                ? testCapabilitiesParameters.sourceParameters
                                : []
                        }
                    />
                </ContentSize>
                {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}

                <AppendFromLiveDataButton
                    handleGenerateTestData={handleGenerateTestDataForSingleSource}
                    maxTestingRecords={maxTestingRecords}
                    recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                />
            </Expandable>
        </StyledStack>
    );
};
