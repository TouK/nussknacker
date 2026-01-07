import loadable from "@loadable/component";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { getTestParameters } from "../../../../reducers/selectors/graph";
import { getTestResultsLoading } from "../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import type { AdhocTestingData, AdhocTestingViewParams } from "../../../modals/AdhocTesting/AdhocTestingDialog";
import { useAdhocTestingAction } from "../../../modals/AdhocTesting/useAdhocTestingAction";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { PropsOfButton } from "../../../toolbarSettings/buttons/types";

export type AdhocTestingButtonProps = {
    type: CustomButtonTypes.adhocTesting;
    name?: string;
    title?: string;
    docs?: AdhocTestingViewParams["docs"];
    markdownContent?: AdhocTestingViewParams["markdownContent"];
};

const AdhocTestingIcon = loadable(() => import("../../../../assets/img/toolbarButtons/test-with-form.svg"));

function AdhocTestingButton({ disabled, name, title, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.adhocTesting>) {
    const { t } = useTranslation();
    const { open, inform } = useWindows();

    const isAvailable = useAdhocTestingAvailability(disabled);

    const testParameters = useAppSelector(getTestParameters);
    const isLoading = useAppSelector(getTestResultsLoading);
    const sourcesFound = testParameters.length;

    const multipleSourcesTest = useCallback(() => {
        inform({ text: `Ad hoc testing is supported only for scenario with single source. Your scenario has ${sourcesFound} sources.` });
    }, [inform, sourcesFound]);

    const action = useAdhocTestingAction();
    const oneSourceTest = useCallback(() => {
        open<AdhocTestingData>({
            title: t("dialog.title.adhoc-testing.test", "Test scenario"),
            isResizable: true,
            kind: WindowKind.adhocTesting,
            meta: {
                view: {
                    Icon: AdhocTestingIcon,
                    docs,
                    markdownContent,
                },
                action,
            },
        });
    }, [action, docs, markdownContent, open, t]);

    return (
        <ToolbarButton
            name={name || t("panels.actions.adhoc-testing.button.name", "ad hoc")}
            title={title || t("panels.actions.adhoc-testing.button.title", "run test on ad hoc data")}
            icon={<AdhocTestingIcon />}
            isLoading={isLoading}
            disabled={!isAvailable || isLoading}
            onClick={(e) => {
                if (sourcesFound > 1) {
                    return multipleSourcesTest();
                }
                if (action.previousTestData && e.shiftKey) {
                    return action.onConfirmAction(action.previousTestData);
                }
                return oneSourceTest();
            }}
            type={type}
        />
    );
}

export default AdhocTestingButton;
