import { EditOutlined } from "@mui/icons-material";
import { Box, InputBase, styled, Tooltip, Typography } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { setTestCaseName } from "../../../../../../actions/nk/testCasesActions";
import { changeActiveTestCase } from "../../../../../../actions/nk/testingActions";
import { getBorderColor } from "../../../../../../containers/theme/helpers";
import { getSettings } from "../../../../../../reducers/selectors/settings";
import { getActiveTestCaseOption, getTestCaseOptions } from "../../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { useTestCaseNameValidation } from "../../../../../modals/useTestCaseNameValidation";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";

export const TestCaseSelect = () => {
    const { t } = useTranslation();
    const settings = useAppSelector(getSettings);
    const dispatch = useAppDispatch();

    const testCaseOptions = useAppSelector(getTestCaseOptions);
    const activeTestCaseOption = useAppSelector(getActiveTestCaseOption);

    const [isEditing, setIsEditing] = useState(false);
    const [editValue, setEditValue] = useState("");

    const { nameErrors, isValid } = useTestCaseNameValidation(editValue, activeTestCaseOption?.label);
    const editErrorMessage = nameErrors[0]?.message;

    const startEditing = useCallback(() => {
        setEditValue(activeTestCaseOption?.label ?? "");
        setIsEditing(true);
    }, [activeTestCaseOption?.label]);

    const confirmEdit = useCallback(() => {
        if (!isValid) return;
        const trimmed = editValue.trim();
        if (trimmed && trimmed !== activeTestCaseOption?.label) {
            dispatch(setTestCaseName(trimmed));
        }
        setIsEditing(false);
    }, [dispatch, editValue, activeTestCaseOption?.label, isValid]);

    const cancelEdit = useCallback(() => {
        setIsEditing(false);
    }, []);

    const handleBlur = useCallback(() => {
        if (isValid) {
            confirmEdit();
        } else {
            cancelEdit();
        }
    }, [isValid, cancelEdit, confirmEdit]);

    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === "Enter") {
                confirmEdit();
            } else if (e.key === "Escape") {
                e.stopPropagation();
                cancelEdit();
            }
        },
        [confirmEdit, cancelEdit],
    );

    const { open } = useWindows();
    const onDisplayEnterpriseInfo = useCallback(() => {
        open({ kind: WindowKind.enterpriseFeatureInfo, layoutData: { width: 500 } });
    }, [open]);

    const openSaveAsDialog = useCallback(() => {
        open({ kind: WindowKind.saveAsTestCase, title: "Save as", layoutData: { width: 500 } });
    }, [open]);

    const handleSaveAsClick = useCallback(() => {
        if (settings.featuresSettings.testCases.multipleEnabled) {
            openSaveAsDialog();
        } else {
            onDisplayEnterpriseInfo();
        }
    }, [onDisplayEnterpriseInfo, openSaveAsDialog, settings.featuresSettings.testCases.multipleEnabled]);

    return (
        <Box ml={4} pt={1.25} display={"flex"} gap={1} alignItems={"center"}>
            <TestCaseField
                options={testCaseOptions}
                activeOption={activeTestCaseOption}
                isEditing={isEditing}
                editValue={editValue}
                editErrorMessage={editErrorMessage}
                onEditValueChange={setEditValue}
                onEditBlur={handleBlur}
                onEditKeyDown={handleKeyDown}
            />
            <InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>
                <StyledButton
                    data-testid="edit-test-case-name"
                    onClick={startEditing}
                    disabled={isEditing}
                    sx={{ display: "flex", justifyContent: "center", alignItems: "center" }}
                >
                    <EditOutlined fontSize="small" />
                </StyledButton>
            </InfoTooltip>
            <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
                <StyledButton data-testid="save-as-test-case" title={t("node.row.add.title", "Add test case")} onClick={handleSaveAsClick}>
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
    overflow: "hidden",
    paddingLeft: theme.spacing(1.5),
    paddingRight: theme.spacing(1.5),
    border: `1px solid ${getBorderColor(theme)}`,
}));

const StyledTestCaseInput = styled(InputBase, {
    shouldForwardProp: (prop) => prop !== "hasError",
})<{ hasError?: boolean }>(({ theme, hasError }) => ({
    width: "40cqw",
    maxWidth: "400px",
    height: "100%",
    border: `1px solid ${hasError ? theme.palette.error.main : theme.palette.primary.main}`,
    paddingLeft: theme.spacing(1.5),
    paddingRight: theme.spacing(1.5),
    fontSize: theme.typography.body2.fontSize,
}));

type TestCaseFieldProps = {
    options: { label: string; value: string }[];
    activeOption: { label: string; value: string } | null;
    isEditing: boolean;
    editValue: string;
    editErrorMessage: string | undefined;
    onEditValueChange: (value: string) => void;
    onEditBlur: () => void;
    onEditKeyDown: (e: React.KeyboardEvent) => void;
};

const TestCaseField = ({
    options,
    activeOption,
    isEditing,
    editValue,
    editErrorMessage,
    onEditValueChange,
    onEditBlur,
    onEditKeyDown,
}: TestCaseFieldProps) => {
    const dispatch = useAppDispatch();

    const changeActiveTestCaseOption = useCallback(
        (testCaseId: string) => {
            dispatch(changeActiveTestCase(testCaseId));
        },
        [dispatch],
    );

    if (isEditing) {
        return (
            <Tooltip open={!!editErrorMessage} title={editErrorMessage ?? ""} placement="bottom-start">
                <StyledTestCaseInput
                    autoFocus
                    hasError={!!editErrorMessage}
                    value={editValue}
                    onChange={(e) => onEditValueChange(e.target.value)}
                    onBlur={onEditBlur}
                    onKeyDown={onEditKeyDown}
                    inputProps={{ "aria-label": "edit test case name" }}
                />
            </Tooltip>
        );
    }

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
