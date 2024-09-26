import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { ElementType, ReactElement, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioGraph, UIParameter, VariableTypes } from "../../../types";
import { WindowContent, WindowKind } from "../../../windowManager";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import { MarkdownForm } from "./MarkdownForm";
import { ActionValues, AdhocTestingFormContext } from "./AdhocTestingFormContext";
import { Box } from "@mui/material";
import { useAdhocTestingParametersValidation } from "./useAdhocTestingParametersValidation";

type DocsLink = {
    url: string;
    label?: string;
};

export type AdhocTestingViewParams = {
    confirmText?: string;
    cancelText?: string;
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a ::form-fields or ::form-field{name=""} directives
    markdownContent?: string;
};

export interface AdhocTestingParameters {
    parameters: UIParameter[];
    variableTypes: VariableTypes;
    processingType: string;
    scenarioName: string;
    initialValues: ActionValues;
    onConfirmAction: (values: ActionValues) => void;
    sourceId: string;
    scenarioGraph: ScenarioGraph;
}

export interface AdhocTestingData {
    view: AdhocTestingViewParams;
    action: AdhocTestingParameters;
}

function AdhocTestingDialog(props: WindowContentProps<WindowKind, AdhocTestingData>): ReactElement {
    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { action, view },
        kind,
    } = data;
    const { variableTypes, parameters = [], initialValues, onConfirmAction } = action;

    const [value, setValue] = useState(initialValues);
    const { errors, isValid } = useAdhocTestingParametersValidation(action, value);

    const confirm = useCallback(async () => {
        onConfirmAction(value);
        close();
    }, [close, onConfirmAction, value]);

    const buttons: WindowButtonProps[] = useMemo(() => {
        return [
            {
                title: t(`dialog.adhoc-testing.button.cancel`, "Cancel"),
                action: () => close(),
                classname: LoadingButtonTypes.secondaryButton,
            },
            {
                title: t(`dialog.adhoc-testing.button.test`, "Test"),
                action: () => confirm(),
                disabled: !isValid,
            },
        ];
    }, [close, confirm, isValid, view.cancelText, view.confirmText, t]);

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            icon={<WindowHeaderIconStyled as={view.Icon} type={kind} />}
            subheader={<NodeDocs name={view.docs?.label} href={view.docs?.url} />}
        >
            <ContentSize>
                <Box mx={3}>
                    <AdhocTestingFormContext.Provider
                        value={{
                            value,
                            setValue,
                            parameters,
                            variableTypes,
                            errors,
                        }}
                    >
                        <MarkdownForm content={view.markdownContent} />
                    </AdhocTestingFormContext.Provider>
                </Box>
            </ContentSize>
        </WindowContent>
    );
}

export default AdhocTestingDialog;
