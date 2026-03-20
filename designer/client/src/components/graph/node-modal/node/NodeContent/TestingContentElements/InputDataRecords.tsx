import React, { useCallback, useMemo, useState } from "react";

import { TestCapabilityStatus } from "../../../../../../common/TestResultUtils";
import HttpService from "../../../../../../http/HttpService/instance";
import { getProcessName, getTestCapabilities as getTestCapabilitiesState } from "../../../../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import type { ScenarioGraph } from "../../../../../../types/scenarioGraph";
import { Expandable } from "../../../../../common/Expandable";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import type { TestingDataRecords } from "../../../../../modals/TestingDataRecords/Table";
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
    } = useDataRecordsActions(scenarioGraph);

    const scenarioName = useAppSelector(getProcessName);
    const scenarioProperties = useAppSelector(getProcessProperties);

    const testCapabilities = useAppSelector(getTestCapabilitiesState);
    const testCapabilitiesParameters = testCapabilities?.testWithParameters;

    const getDefaultRecord = useCallback(async (): Promise<TestingDataRecords> => {
        const { data: capabilities } = await HttpService.getTestCapabilities(scenarioName, scenarioGraph);
        const sourceParam =
            capabilities.testWithParameters.status === TestCapabilityStatus.AVAILABLE
                ? capabilities.testWithParameters.sourceParameters.find((p) => p.sourceId === sourceId)
                : undefined;
        return {
            sourceId,
            variables: sourceParam?.parameters?.[0]?.defaultValue?.expression ?? "",
        };
    }, [scenarioName, scenarioGraph, sourceId]);

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
                        getDefaultRecord={getDefaultRecord}
                        onRowAdded={handleRowAdded}
                        onRowMoved={handleRowMoved}
                        onRowsDeleted={handleRowsDeleted}
                        onRowUpdated={handleRowUpdated}
                        data={testingDataRecordsForSource}
                        sourceOptions={[sourceId]}
                        sourceParameters={
                        // TODO: testCapabilitiesParameters used
                            testCapabilitiesParameters?.status === TestCapabilityStatus.AVAILABLE
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
