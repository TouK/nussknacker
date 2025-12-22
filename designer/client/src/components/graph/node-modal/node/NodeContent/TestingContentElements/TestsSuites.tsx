import SaveAsIcon from "@mui/icons-material/SaveAs";
import { Box, styled } from "@mui/material";
import React, { useCallback } from "react";

import { useWindows } from "../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../windowManager/WindowKind";
import { StyledButton } from "../../../../styledButton";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import type { Option } from "../../../fragment-input-definition/TypeSelect";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";

const StyledActionButton = styled(StyledButton)(() => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
}));

const TestsSuites = () => {
    const { open } = useWindows();
    const options: Option[] = [{ label: "test_suite_1", value: "test_suite_1" }];
    const onDisplayEnterpriseInfo = useCallback(() => {
        open({ kind: WindowKind.enterpriseFeatureInfo, layoutData: { width: 500 } });
    }, [open]);

    return (
        <Box maxWidth={"40%"} pt={2} px={3} display={"flex"} alignItems={"center"} gap={1}>
            <TypeSelect
                width={"30%"}
                options={options}
                onChange={() => {
                    console.log("noop");
                }}
                value={options[0]}
            />
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
    );
};

export default TestsSuites;
