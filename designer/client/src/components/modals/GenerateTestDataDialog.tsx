import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import HttpService from "../../http/HttpService";
import { getProcessName, getScenarioGraph } from "../../reducers/selectors/graph";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { PromptContent } from "../../windowManager";
import {
    extendErrors,
    getValidationErrorsForField,
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../graph/node-modal/editors/Validators";
import { NodeInput } from "../FormElements";
import ValidationLabels from "./ValidationLabels";
import { isEmpty } from "lodash";
import { Typography } from "@mui/material";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { nodeInput, nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";

function GenerateTestDataDialog(props: WindowContentProps): JSX.Element {
    const { t } = useTranslation();
    const processName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const maxSize = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ testSampleSize }, setState] = useState({
        //TODO: current validators work well only for string values
        testSampleSize: "10",
    });

    const confirmAction = useCallback(async () => {
        await HttpService.generateTestData(processName, testSampleSize, scenarioGraph);
        props.close();
    }, [processName, testSampleSize, scenarioGraph, props]);

    const validators = [literalIntegerValueValidator, minimalNumberValidator(0), maximalNumberValidator(maxSize), mandatoryValueValidator];
    const errors = extendErrors([], testSampleSize, "testData", validators);
    const isValid = isEmpty(errors);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Apply"), disabled: !isValid, action: () => confirmAction() },
        ],
        [t, confirmAction, props, isValid],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <NodeTable className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h6"}>{t("test-generate.title", "Generate test data")}</Typography>
                <div className={nodeValue}>
                    <NodeInput
                        value={testSampleSize}
                        onChange={(event) => setState({ testSampleSize: event.target.value })}
                        className={nodeInput}
                        autoFocus
                    />
                    <ValidationLabels fieldErrors={getValidationErrorsForField(errors, "testData")} />
                </div>
            </NodeTable>
        </PromptContent>
    );
}

export default GenerateTestDataDialog;
