import { Box, Typography } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDidMount } from "rooks";

import { useUserSettings } from "../../../../../../common/useUserSettings";
import HttpService from "../../../../../../http/HttpService/instance";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { Expandable } from "../../../../../common/Expandable";
import MockExpressionField from "../../../editors/expression/MockExpressionField";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { getFindAvailableVariables, getProcessName, getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { useGetNodeTestCasesErrors, useIsEditMode, useSetProperty, useValidation } from "../../../useNodeTypeDetailsContentLogic";
import type { TestingContentProps } from "../TestingContent";
import { StyledStack } from "./components/Styled";

const MOCK_EXPRESSION_HINT_TEXT =
    "If you provide this expression, the real service won't be invoked during tests. Instead, the result of the evaluation will be used.";

export const MockResponse = ({ node, edges, onChange }: TestingContentProps) => {
    const { t } = useTranslation();

    const [isExpanded, setIsExpanded] = useState(true);
    const isEditMode = useIsEditMode({ onChange });
    const setProperty = useSetProperty({ onChange, node });
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const testCasesErrors = useGetNodeTestCasesErrors(node);
    const scenarioName = useAppSelector(getProcessName);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);
    const processProperties = useAppSelector(getProcessProperties);
    const [defaultValue, setDefaultValue] = useState(null);
    const [shouldGenerate] = useUserSettings("editor.showResetToDefaultButton");

    useValidation({ node, showValidation: true, edges });

    const generateEnricherMockExpression = useCallback(() => {
        HttpService.generateEnricherMockExpression(scenarioName, {
            variableTypes,
            processProperties,
            enricher: node,
        }).then((data) => {
            if (data?.enricherMockExpression) {
                setDefaultValue(data.enricherMockExpression);
            }
        });
    }, [node, processProperties, scenarioName, variableTypes]);

    useDidMount(() => {
        if (shouldGenerate) {
            generateEnricherMockExpression();
        }
    });

    return (
        <StyledStack>
            <Expandable
                componentId={"mock"}
                expandableTitle={
                    <Box display={"flex"} gap={2} alignItems={"center"}>
                        <Typography variant={"body2"} color={"text.secondary"}>
                            {t("testingDialog.label.mock", "Mock")}
                        </Typography>
                        <InfoTooltip title={t("testingDialog.mock.hint", MOCK_EXPRESSION_HINT_TEXT)} variant={"hover"} />
                    </Box>
                }
                expanded={isExpanded}
                onChange={setIsExpanded}
            >
                <MockExpressionField
                    isEditMode={isEditMode}
                    editedNode={node}
                    showValidation
                    showSwitch
                    variableTypes={variableTypes}
                    setNodeDataAt={setProperty}
                    errors={testCasesErrors.enricherMockErrors}
                    defaultValue={defaultValue}
                />
            </Expandable>
        </StyledStack>
    );
};
