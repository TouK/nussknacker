import { Box, styled, Typography } from "@mui/material";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { WindowContent, WindowKind } from "../../windowManager";
import { ProcessStateType, Scenario } from "../Process/types";
import { DATE_FORMAT } from "../../config";
import moment from "moment";
import i18next from "i18next";
import { capitalize, startCase } from "lodash";
import { getProcessingModeVariantName } from "../toolbars/scenarioDetails/getProcessingModeVariantName";
import NuLogoIcon from "../../assets/img/nussknacker-logo-icon.svg";

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

    const displayStatus = !scenario.isArchived && !scenario.isFragment;
    const displayLabels = scenario.labels.length !== 0;

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            components={{
                Content: () => (
                    <Box width={"500px"} mx={"auto"} p={2}>
                        <Box display={"flex"} justifyContent={"center"} mb={2}>
                            <Box
                                component={NuLogoIcon}
                                sx={(theme) => ({ color: theme.palette.primary.main, width: "2rem", height: "auto" })}
                            />
                        </Box>

                        <Typography component={"div"} textAlign={"center"} variant={"caption"} fontWeight={"li"}>
                            {i18next.t("scenarioDetails.header", "Nussknacker scenario:")}
                        </Typography>
                        <Typography sx={{ wordWrap: "break-word" }} textAlign={"center"} variant={"subtitle2"}>
                            {scenario.name}
                        </Typography>
                        <Box display={"flex"} flexDirection={"column"} mt={3} mb={3}>
                            {displayStatus && (
                                <ItemWrapperStyled>
                                    <ItemLabelStyled>{i18next.t("scenarioDetails.label.status", "Status")}</ItemLabelStyled>
                                    <Typography variant={"caption"}>{capitalize(startCase(processState.status.name))}</Typography>
                                </ItemWrapperStyled>
                            )}
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.processingMode", "Processing mode")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{getProcessingModeVariantName(scenario.processingMode)}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.category", "Category")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{scenario.processCategory}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.engine", "Engine")}</ItemLabelStyled>
                                <Typography variant={"caption"}>{scenario.engineSetupName}</Typography>
                            </ItemWrapperStyled>
                            {displayLabels && (
                                <ItemWrapperStyled>
                                    <ItemLabelStyled>{i18next.t("scenarioDetails.label.labels", "Labels")}</ItemLabelStyled>
                                    <Typography variant={"caption"}>{scenario.labels.join(", ")}</Typography>
                                </ItemWrapperStyled>
                            )}
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
