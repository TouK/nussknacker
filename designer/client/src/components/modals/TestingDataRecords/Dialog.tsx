import { Box, Stack, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithDataRecords } from "../../../actions/nk/displayTestResults";
import { getTestCapabilities, getTestingDataRecords } from "../../../reducers/selectors/graph";
import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowContent } from "../../../windowManager/WindowContent";
import type { WindowKind } from "../../../windowManager/WindowKind";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { AppendFromLiveDataButton } from "./AppendFromLiveDataButton";
import { LimitExceededWarning } from "./LimitExceededWarning";
import { Table } from "./Table";
import { useDataRecordsActions } from "./useDataRecordsActions";

type DocsLink = {
    url: string;
    label?: string;
};

export type TestingViewParams = {
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a ::form-fields or ::form-field{name=""} directives
    markdownContent?: string;
};

export interface TestingData {
    viewParams: TestingViewParams;
}

function Dialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const maxTestingRecords = useAppSelector(getMaxTestingRecords);

    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { viewParams },
        kind,
    } = data;

    const dispatch = useAppDispatch();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const testingDataRecords = useAppSelector(getTestingDataRecords);

    const defaultParameter = testCapabilities.testWithParameters.sourceParameters[0];

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

    const { recordsErrors, cellErrors, handleRowUpdated, handleGenerateTestData, handleRowsDeleted, handleRowAdded, handleRowMoved } =
        useDataRecordsActions({
            testingDataRecords,
        });

    const sourceOptions = useMemo(
        () => testCapabilities.testWithParameters.sourceParameters.flatMap((sourceParameter) => sourceParameter.sourceId),
        [testCapabilities.testWithParameters.sourceParameters],
    );

    const recordsToAddLimitExceeded = useMemo(
        () => recordsErrors.some((recordsErrors) => recordsErrors.type === "TEST_DATA_LIMIT_EXCEEDED"),
        [recordsErrors],
    );

    const disableTestButton = testingDataRecords.length === 0 || cellErrors.length > 0;
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("testingForm.cancelButton.label", "Cancel"), action: () => close(), classname: LoadingButtonTypes.secondaryButton },
            {
                disabled: disableTestButton,
                title: t("testingForm.testButton.label", "Test"),
                action: () => {
                    try {
                        dispatch(testScenarioWithDataRecords(testingDataRecords));
                        close();
                    } catch (e) {
                        console.error(e.message);
                    }
                },
            },
        ],
        [close, disableTestButton, dispatch, testingDataRecords, t],
    );

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={
                <Box display={"flex"} alignItems={"center"}>
                    <InfoTooltip
                        variant={"hover"}
                        title={t(
                            "testingDialog.description",
                            `Use prepared set of input data records to verify the scenario before deploying it. <br /> The sources will be stubbed with data records below during test invocation.`,
                        )}
                    />
                </Box>
            }
            buttons={buttons}
        >
            <ContentSize sx={{ paddingX: 3, paddingY: 3 }}>
                <Stack spacing={2}>
                    <Typography m={0} variant="h3">
                        {t("testingDialog.label.inputDataRecords", "Input data records")}
                    </Typography>
                    <Table
                        sourceOptions={sourceOptions}
                        sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                        data={testingDataRecords}
                        cellErrors={cellErrors}
                        onRowUpdated={handleRowUpdated}
                        onRowAdded={handleRowAdded}
                        onRowsDeleted={handleRowsDeleted}
                        onRowMoved={handleRowMoved}
                        defaultDataRecord={defaultDataRecord}
                        recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                    />
                    {recordsToAddLimitExceeded ? <LimitExceededWarning maxTestingRecords={maxTestingRecords} /> : null}
                    <AppendFromLiveDataButton
                        handleGenerateTestData={handleGenerateTestData}
                        maxTestingRecords={maxTestingRecords}
                        currentRecordsNumber={testingDataRecords.length}
                        recordsToAddLimitExceeded={recordsToAddLimitExceeded}
                    />
                </Stack>
            </ContentSize>
        </WindowContent>
    );
}
export default Dialog;
