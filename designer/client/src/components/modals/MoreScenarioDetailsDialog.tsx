import { Box, styled, SvgIcon, Typography } from "@mui/material";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { WindowContent, WindowKind } from "../../windowManager";
import { ProcessStateType, Scenario } from "../Process/types";
import { DATE_FORMAT } from "../../config";
import moment from "moment";
import i18next from "i18next";
import { capitalize, startCase } from "lodash";
import { ProcessingMode } from "../../http/HttpService";
import NuLogoIcon from "../../assets/img/nuLogoIcon.svg";

const ItemWrapperStyled = styled("div")({ display: "grid", gridAutoColumns: "minmax(0, 1fr)", gridAutoFlow: "column" });

const ItemLabelStyled = styled(Typography)({ display: "flex", justifyContent: "flex-end", marginRight: "8px" });

ItemLabelStyled.defaultProps = {
    variant: "caption",
    fontWeight: 300,
};

interface Props {
    scenario: Scenario;
    processState: ProcessStateType;
}

function MoreScenarioDetailsDialog(props: WindowContentProps<WindowKind, Props>): JSX.Element {
    const { scenario, processState } = props.data.meta;

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: t("dialog.button.info.confirm", "Ok"),
                action: () => props.close(),
            },
        ],
        [props, t],
    );

    const processingModeVariantName = useMemo(() => {
        switch (scenario.processingMode) {
            case ProcessingMode.batch: {
                return i18next.t(`scenarioDetails.processingModeVariants.batch`, "Batch");
            }

            case ProcessingMode.requestResponse: {
                return i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response");
            }
            case ProcessingMode.streaming:
                return i18next.t(`scenarioDetails.processingModeVariants.streaming`, "Streaming");
        }
    }, [scenario.processingMode]);

    const displayStatus = !scenario.isArchived && !scenario.isFragment;

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            components={{
                Content: () => (
                    <Box width={"500px"} mx={"auto"} p={2}>
                        <Box display={"flex"} justifyContent={"center"} mb={2}>
                            <SvgIcon color={"primary"} fontSize={"large"}>
                                <NuLogoIcon />
                            </SvgIcon>
                        </Box>

                        <Typography component={"div"} textAlign={"center"} variant={"caption"} fontWeight={"li"}>
                            {i18next.t("scenarioDetails.header", "Nussknacker scenario:")}
                        </Typography>
                        <Typography sx={{ wordWrap: "break-word" }} textAlign={"center"} variant={"subtitle2"}>
                            {scenario.name}
                        </Typography>
                        <Box display={"flex"} flexDirection={"column"} mt={4} mb={4}>
                            {displayStatus && (
                                <ItemWrapperStyled>
                                    <ItemLabelStyled>{i18next.t("scenarioDetails.label.status", "Status")}</ItemLabelStyled>
                                    <Typography variant={"caption"}>{capitalize(startCase(processState.status.name))}</Typography>
                                </ItemWrapperStyled>
                            )}
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.processingMode", "Processing mode")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{processingModeVariantName}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.category", "Category")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{scenario.processCategory}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.engine", "Engine")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{scenario.engineSetupName}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.created", "Created")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{moment(scenario.createdAt).format(DATE_FORMAT)}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.lastModified", "Last modified")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{moment(scenario.modifiedAt).format(DATE_FORMAT)}</Typography>
                            </ItemWrapperStyled>
                        </Box>
                    </Box>
                ),
            }}
        ></WindowContent>
    );
}

export default MoreScenarioDetailsDialog;
