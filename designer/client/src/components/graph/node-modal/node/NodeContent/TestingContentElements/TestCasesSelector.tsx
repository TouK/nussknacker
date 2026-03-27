import { Box, styled, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { changeActiveTestCase } from "../../../../../../actions/nk/testingActions";
import { getBorderColor } from "../../../../../../containers/theme/helpers";
import { getSettings } from "../../../../../../reducers/selectors/settings";
import { getActiveTestCaseOption, getTestCaseOptions } from "../../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";

export const TestCasesSelector = () => {
    const { t } = useTranslation();
    const settings = useAppSelector(getSettings);

    const testCaseOptions = useAppSelector(getTestCaseOptions);
    const activeTestCaseOption = useAppSelector(getActiveTestCaseOption);

    const dispatch = useAppDispatch();

    const { open } = useWindows();
    const onDisplayEnterpriseInfo = useCallback(() => {
        open({ kind: WindowKind.enterpriseFeatureInfo, layoutData: { width: 500 } });
    }, [open]);

    const openSaveAsDialog = useCallback(() => {
        open({ kind: WindowKind.saveAsTestCase, title: "Save as", layoutData: { width: 500 } });
    }, [open]);

    const changeActiveTestCaseOption = useCallback(
        (testCaseId: string) => {
            dispatch(changeActiveTestCase(testCaseId));
        },
        [dispatch],
    );

    const handleSaveAsClick = useCallback(() => {
        if (settings.featuresSettings.testCases.multipleEnabled) {
            openSaveAsDialog();
        } else {
            onDisplayEnterpriseInfo();
        }
    }, [onDisplayEnterpriseInfo, openSaveAsDialog, settings.featuresSettings.testCases.multipleEnabled]);

    return (
        <Box ml={4} pt={1.25} display={"flex"} gap={1}>
            <StyledTestCasesSelect options={testCaseOptions} onChange={changeActiveTestCaseOption} value={activeTestCaseOption} />
            {/*<InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>*/}
            {/*    <StyledActionButton>*/}
            {/*        <EditIcon />*/}
            {/*    </StyledActionButton>*/}
            {/*</InfoTooltip>*/}
            <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
                <StyledButton data-testid="save-as-test-case" title={t("node.row.add.title", "Add field")} onClick={handleSaveAsClick}>
                    {t("node.row.add.text", "+")}
                </StyledButton>
            </InfoTooltip>
        </Box>
    );
};

const StyledTestCasesSelect = styled(TypeSelect)(() => ({
    width: "40cqw",
    maxWidth: "400px",
}));

const StyledTestCaseLabel = styled(Box)(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    height: "100%",
    width: "40cqw",
    maxWidth: "400px",
    paddingLeft: theme.spacing(1.5),
    paddingRight: theme.spacing(1.5),
    border: `1px solid ${getBorderColor(theme)}`,
}));

type TestCaseFieldProps = {
    options: { label: string; value: string }[];
    activeOption: { label: string; value: string } | null;
};

const TestCaseField = ({ options, activeOption }: TestCaseFieldProps) => {
    const dispatch = useAppDispatch();

    const changeActiveTestCaseOption = useCallback(
        (testCaseId: string) => {
            dispatch(changeActiveTestCase(testCaseId));
        },
        [dispatch],
    );

    if (options.length > 1) {
        return <StyledTestCasesSelect options={options} onChange={changeActiveTestCaseOption} value={activeOption} />;
    }
    return (
        <StyledTestCaseLabel>
            <Typography variant="body2" noWrap>
                {activeOption?.label}
            </Typography>
        </StyledTestCaseLabel>
    );
};
