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
import { ProcessingMode } from "../../http/HttpService";

const ItemWrapperStyled = styled("div")({ display: "grid", gridAutoColumns: "minmax(0, 1fr)", gridAutoFlow: "column" });

const ItemLabelStyled = styled(Typography)({ display: "flex", justifyContent: "flex-end", marginRight: "8px" });

ItemLabelStyled.defaultProps = {
    variant: "subtitle2",
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

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            components={{
                Content: () => (
                    <Box width={"400px"} mx={"auto"}>
                        <Typography textAlign={"center"} variant={"subtitle1"}>
                            {i18next.t("scenarioDetails.header", "Nussknacker scenario:")}
                        </Typography>
                        <Typography textAlign={"center"} variant={"subtitle1"}>
                            {scenario.name}
                        </Typography>
                        <Box display={"flex"} flexDirection={"column"} mt={4} mb={6}>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.status", "Status")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{capitalize(startCase(processState.status.name))}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.processingMode", "Processing mode")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{processingModeVariantName}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.category", "Category")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{scenario.processCategory}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.engine", "Engine")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{scenario.engineSetupName}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.created", "Created")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{moment(scenario.createdAt).format(DATE_FORMAT)}</Typography>
                            </ItemWrapperStyled>
                            <ItemWrapperStyled>
                                <ItemLabelStyled>{i18next.t("scenarioDetails.label.lastModified", "Last modified")}</ItemLabelStyled>
                                <Typography variant={"body2"}>{moment(scenario.modifiedAt).format(DATE_FORMAT)}</Typography>
                            </ItemWrapperStyled>
                        </Box>
                    </Box>
                ),
            }}
        ></WindowContent>
    );
}

export default MoreScenarioDetailsDialog;
