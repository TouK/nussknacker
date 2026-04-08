import React, { useCallback, useEffect, useMemo, useState } from "react";

import { TestCapabilityStatus } from "../../../../../../common/TestResultUtils";
import type { TestFormParameters } from "../../../../../../common/TestResultUtils";
import HttpService from "../../../../../../http/HttpService/instance";
import { getMaxTestingRecords } from "../../../../../../reducers/selectors/settings";
import { getInputDataRecordsForSingleSource } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { AppendFromLiveDataButton } from "../../../../../modals/TestingDataRecords/AppendFromLiveDataButton";
import { LimitExceededWarning } from "../../../../../modals/TestingDataRecords/LimitExceededWarning";
import { Table } from "../../../../../modals/TestingDataRecords/Table";
import type { TestingDataRecords } from "../../../../../modals/TestingDataRecords/types";
import { useDataRecordsActions } from "../../../../../modals/TestingDataRecords/useDataRecordsActions";
import { buildDefaultVariables } from "../../../../../modals/TestingDataRecords/utils";
import { getProcessName, getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { cleanProperties } from "../../../requestSourceAddons";
import { ContentSize } from "../../ContentSize";
import { StyledStack } from "./components/Styled";
import { TestingExpandable } from "./components/TestingExpandable";

interface Props {
    node: NodeType;
}

export const InputDataRecords = ({ node }: Props) => {
    const [isExpanded, setIsExpanded] = useState(true);
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const testingDataRecordsForSource = useAppSelector((state) => getInputDataRecordsForSingleSource(state, node.id));
    const scenarioProperties = useAppSelector(getProcessProperties);
    const scenarioName = useAppSelector(getProcessName);

    const [sourceParameters, setSourceParameters] = useState<TestFormParameters | undefined>(undefined);

    useEffect(() => {
        HttpService.getSourceTestCapabilities(scenarioName, scenarioProperties, cleanProperties(node)).then(({ data }) => {
            const capabilities = data.testWithParameters;
            setSourceParameters(capabilities.status === TestCapabilityStatus.AVAILABLE ? capabilities.sourceParameters : undefined);
        });
    }, [scenarioName, scenarioProperties, node]);

    const {
        cellErrors,
        recordsErrors,
        handleRowAdded,
        handleRowMoved,
        handleRowsDeleted,
        handleRowUpdated,
        generateTestDataForSingleSource,
    } = useDataRecordsActions(node, scenarioProperties);

    const defaultDataRecord = useMemo(
        () => ({
            sourceId: node.id,
            timestamp: undefined,
            variables: buildDefaultVariables(sourceParameters?.parameters),
        }),
        [node.id, sourceParameters],
    );

    const onValidateVariables = useCallback(
        (row: TestingDataRecords) => HttpService.validateSourceNodeTestData(scenarioName, scenarioProperties, cleanProperties(node), row),
        [scenarioName, scenarioProperties, node],
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
            <TestingExpandable
                componentId={"inputDataRecords"}
                expandableTitle={"Test data"}
                expanded={isExpanded}
                onChange={setIsExpanded}
            >
                <ContentSize sx={{ padding: 0, maxHeight: "45cqh", mb: 2 }}>
                    <Table
                        data={testingDataRecordsForSource}
                        onRowAdded={handleRowAdded}
                        onRowUpdated={handleRowUpdated}
                        onRowsDeleted={handleRowsDeleted}
                        onRowMoved={handleRowMoved}
                        defaultDataRecord={defaultDataRecord}
                        sourceId={node.id}
                        sourceName={node.name}
                        sourceParameters={sourceParameters}
                        cellErrors={cellErrors}
                        onValidateVariables={onValidateVariables}
                    />
                </ContentSize>
                {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}

                <AppendFromLiveDataButton
                    handleGenerateTestData={handleGenerateTestDataForSingleSource}
                    maxTestingRecords={maxTestingRecords}
                    recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                />
            </TestingExpandable>
        </StyledStack>
    );
};
