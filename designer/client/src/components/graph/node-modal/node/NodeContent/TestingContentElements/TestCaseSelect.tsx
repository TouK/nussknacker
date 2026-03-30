import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined } from "@mui/icons-material";
import { Box, Divider, FormHelperText, InputBase, styled, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { changeActiveTestCase } from "../../../../../../actions/nk/testingActions";
import { getBorderColor } from "../../../../../../containers/theme/helpers";
import { getLoggedUser, getSettings } from "../../../../../../reducers/selectors/settings";
import { getActiveTestCaseOption, getTestCaseOptions } from "../../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";
import { useTestCaseDelete } from "./useTestCaseDelete";
import { useTestCaseNameEdit } from "./useTestCaseNameEdit";

export const TestCaseSelect = () => {
    const { t } = useTranslation();
    const settings = useAppSelector(getSettings);
    const loggedUser = useAppSelector(getLoggedUser);

    const testCaseOptions = useAppSelector(getTestCaseOptions);
    const activeTestCaseOption = useAppSelector(getActiveTestCaseOption);

    const { isEditing, editValue, editErrorMessage, setEditValue, startEditing, handleBlur, handleKeyDown } = useTestCaseNameEdit(
        activeTestCaseOption?.label,
    );

    const {
        isConfirming,
        isDisabled: isDeleteDisabled,
        disabledTooltip,
        startDeleting,
        cancelDelete,
        confirmDelete,
    } = useTestCaseDelete(activeTestCaseOption?.value, testCaseOptions.length, isEditing);

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
            {loggedUser.isWriter() && (
                <>
                    {isConfirming ? (
                        <>
                            <InfoTooltip title={t("testCaseDelete.confirm", "Confirm deletion")} variant={"hover"} enterDelay={500}>
                                <StyledButton data-testid="confirm-delete-test-case" onClick={confirmDelete}>
                                    <CheckOutlined fontSize="small" />
                                </StyledButton>
                            </InfoTooltip>
                            <InfoTooltip title={t("testCaseDelete.cancel", "Cancel deletion")} variant={"hover"} enterDelay={500}>
                                <StyledButton data-testid="cancel-delete-test-case" onClick={cancelDelete}>
                                    <CloseOutlined fontSize="small" />
                                </StyledButton>
                            </InfoTooltip>
                        </>
                    ) : (
                        <>
                            <InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>
                                <StyledButton data-testid="edit-test-case-name" onClick={startEditing} disabled={isEditing}>
                                    <EditOutlined fontSize="small" />
                                </StyledButton>
                            </InfoTooltip>
                            <InfoTooltip
                                title={disabledTooltip ?? t("testCaseDelete.delete", "Delete test case")}
                                variant={"hover"}
                                enterDelay={500}
                            >
                                <span>
                                    <StyledButton data-testid="delete-test-case" onClick={startDeleting} disabled={isDeleteDisabled}>
                                        <DeleteOutlined fontSize="small" />
                                    </StyledButton>
                                </span>
                            </InfoTooltip>
                        </>
                    )}
                    <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
                    <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
                        <StyledButton
                            data-testid="save-as-test-case"
                            title={t("node.row.add.title", "Add test case")}
                            onClick={handleSaveAsClick}
                        >
                            {t("node.row.add.text", "+")}
                        </StyledButton>
                    </InfoTooltip>
                </>
            )}
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
            <Box position="relative" height="100%">
                <StyledTestCaseInput
                    autoFocus
                    hasError={!!editErrorMessage}
                    value={editValue}
                    onChange={(e) => onEditValueChange(e.target.value)}
                    onBlur={onEditBlur}
                    onKeyDown={onEditKeyDown}
                    inputProps={{ "aria-label": "edit test case name" }}
                />
                {editErrorMessage && (
                    <FormHelperText
                        error
                        title={editErrorMessage}
                        sx={{ position: "fixed", mt: 0.5, zIndex: "tooltip", whiteSpace: "nowrap" }}
                    >
                        {editErrorMessage}
                    </FormHelperText>
                )}
            </Box>
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
