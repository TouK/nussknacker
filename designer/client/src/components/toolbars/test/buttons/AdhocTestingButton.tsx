import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getTestParameters } from "../../../../reducers/selectors/graph";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { useWindows, WindowKind } from "../../../../windowManager";
import { AdhocTestingData, AdhocTestingViewParams } from "../../../modals/AdhocTesting/AdhocTestingDialog";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import { useAdhocTestingAction } from "../../../modals/AdhocTesting/useAdhocTestingAction";
import { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import UrlIcon from "../../../UrlIcon";
import loadable from "@loadable/component";

export type AdhocTestingButtonProps = {
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

    const testParameters = useSelector(getTestParameters);
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
                view: { Icon: AdhocTestingIcon, docs, markdownContent },
                action,
            },
        });
    }, [action, docs, markdownContent, open, t]);

    return (
        <ToolbarButton
            name={name || t("panels.actions.adhoc-testing.button.name", "ad hoc")}
            title={title || t("panels.actions.adhoc-testing.button.title", "run test on ad hoc data")}
            icon={<AdhocTestingIcon />}
            disabled={!isAvailable}
            onClick={sourcesFound > 1 ? multipleSourcesTest : oneSourceTest}
            type={type}
        />
    );
}

export default AdhocTestingButton;
