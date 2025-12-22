import { Box, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { useAppSelector } from "../../../../../../store/storeHelpers";
import MockExpressionField from "../../../editors/expression/MockExpressionField";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { getFindAvailableVariables } from "../../../NodeDetailsContent/selectors";
import { useGetNodeErrors, useIsEditMode, useSetProperty, useValidation } from "../../../useNodeTypeDetailsContentLogic";
import type { TestingContentProps } from "../TestingContent";
import { StyledStack } from "./components/Styled";

const MOCK_EXPRESSION_HINT_TEXT =
    "If you provide this expression, the real service won't be invoked during tests. Instead, the result of the evaluation will be used.";

export const MockResponse = ({ node, edges, onChange }: TestingContentProps) => {
    const { t } = useTranslation();

    const isEditMode = useIsEditMode({ onChange });
    const setProperty = useSetProperty({ onChange, node });
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const [errors] = useGetNodeErrors(node);

    useValidation({ node, showValidation: true, edges });

    return (
        <StyledStack>
            <Box display={"flex"} gap={2} alignItems={"center"}>
                <Typography m={0} variant="h5">
                    {t("testingDialog.label.mock", "Mock")}
                </Typography>
                <InfoTooltip title={MOCK_EXPRESSION_HINT_TEXT} variant={"hover"} />
            </Box>

            <MockExpressionField
                isEditMode={isEditMode}
                editedNode={node}
                showValidation
                showSwitch
                findAvailableVariables={findAvailableVariables}
                setNodeDataAt={setProperty}
                errors={errors}
            />
        </StyledStack>
    );
};
