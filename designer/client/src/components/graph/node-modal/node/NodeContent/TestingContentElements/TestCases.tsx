import SaveAsIcon from "@mui/icons-material/SaveAs";
import { Box, styled } from "@mui/material";
import React, { useCallback } from "react";

import { getTestCaseOptions } from "../../../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";
import { StyledStack } from "./components/Styled";

const StyledActionButton = styled(StyledButton)(() => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
}));

const TestCases = () => {
    const testCaseOptions = useAppSelector(getTestCaseOptions);
    const { open } = useWindows();
    const onDisplayEnterpriseInfo = useCallback(() => {
        open({ kind: WindowKind.enterpriseFeatureInfo, layoutData: { width: 500 } });
    }, [open]);

    return (
        <StyledStack sx={{ pb: 2, ml: 1 }}>
            <Box maxWidth={"40%"} pt={2} display={"flex"} alignItems={"center"} gap={1}>
                <TypeSelect width={"30%"} options={testCaseOptions} onChange={() => "noop"} value={testCaseOptions[0]} />
                {/*<InfoTooltip title={"Edit name"} variant={"hover"} enterDelay={500}>*/}
                {/*    <StyledActionButton>*/}
                {/*        <EditIcon />*/}
                {/*    </StyledActionButton>*/}
                {/*</InfoTooltip>*/}
                <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
                    <StyledActionButton onClick={onDisplayEnterpriseInfo}>
                        <SaveAsIcon />
                    </StyledActionButton>
                </InfoTooltip>
            </Box>
        </StyledStack>
    );
};

export default TestCases;
