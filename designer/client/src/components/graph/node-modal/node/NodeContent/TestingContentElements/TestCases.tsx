import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { getTestCaseOptions } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";

export const TestCases = () => {
    const { t } = useTranslation();
    const testCaseOptions = useAppSelector(getTestCaseOptions);
    const { open } = useWindows();
    const onDisplayEnterpriseInfo = useCallback(() => {
        open({ kind: WindowKind.enterpriseFeatureInfo, layoutData: { width: 500 } });
    }, [open]);

    return (
        <Box ml={4} pb={0.25} width={"40%"} pt={2} display={"flex"} alignItems={"center"} gap={1}>
            <TypeSelect width={"30%"} options={testCaseOptions} onChange={() => "noop"} value={testCaseOptions[0]} />
            {/*<InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>*/}
            {/*    <StyledActionButton>*/}
            {/*        <EditIcon />*/}
            {/*    </StyledActionButton>*/}
            {/*</InfoTooltip>*/}
            <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
                <StyledButton title={t("node.row.add.title", "Add field")} onClick={onDisplayEnterpriseInfo}>
                    {t("node.row.add.text", "+")}
                </StyledButton>
            </InfoTooltip>
        </Box>
    );
};
