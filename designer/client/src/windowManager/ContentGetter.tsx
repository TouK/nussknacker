import { WindowContentProps } from "@touk/window-manager";
import { DebugButtons } from "@touk/window-manager/cjs/debug";
import React from "react";
import { Debug } from "../containers/Debug";
import { WindowContent } from "./WindowContent";
import { WindowKind } from "./WindowKind";
import loadable from "@loadable/component";
import LoaderSpinner from "../components/spinner/Spinner";
import FrameDialog from "../components/FrameDialog";
import { NuThemeProvider } from "../containers/theme/nuThemeProvider";

const AddProcessDialog = loadable(() => import("../components/AddProcessDialog"), { fallback: <LoaderSpinner show /> });
const NodeDetails = loadable(() => import("../components/graph/node-modal/node/NodeDetails"), {
    fallback: <LoaderSpinner show />,
});
const CountsDialog = loadable(() => import("../components/modals/CalculateCounts"), { fallback: <LoaderSpinner show /> });
const CompareVersionsDialog = loadable(() => import("../components/modals/CompareVersionsDialog"), {
    fallback: <LoaderSpinner show />,
});
const CustomActionDialog = loadable(() => import("../components/modals/CustomActionDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenericActionDialog = loadable(() => import("../components/modals/GenericActionDialog"), {
    fallback: <LoaderSpinner show />,
});
const DeployProcessDialog = loadable(() => import("../components/modals/DeployProcessDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenericConfirmDialog = loadable(() => import("../components/modals/GenericConfirmDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenericInfoDialog = loadable(() => import("../components/modals/GenericInfoDialog"), {
    fallback: <LoaderSpinner show />,
});
const SaveProcessDialog = loadable(() => import("../components/modals/SaveProcessDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenerateTestDataDialog = loadable(() => import("../components/modals/GenerateTestDataDialog"), {
    fallback: <LoaderSpinner show />,
});
const GenerateDataAndTestDialog = loadable(() => import("../components/modals/GenerateDataAndTestDialog"), {
    fallback: <LoaderSpinner show />,
});

const ScenarioDetailsDialog = loadable(() => import("../components/modals/MoreScenarioDetailsDialog"), {
    fallback: <LoaderSpinner show />,
});

const contentGetter: React.FC<WindowContentProps<WindowKind>> = (props) => {
    switch (props.data.kind) {
        case WindowKind.addFragment:
            return <AddProcessDialog {...props} isFragment />;
        case WindowKind.addProcess:
            return <AddProcessDialog {...props} />;
        case WindowKind.saveProcess:
            return <SaveProcessDialog {...props} />;
        case WindowKind.deployProcess:
            return <DeployProcessDialog {...props} />;
        case WindowKind.calculateCounts:
            return <CountsDialog {...props} />;
        case WindowKind.generateTestData:
            return <GenerateTestDataDialog {...props} />;
        case WindowKind.generateDataAndTest:
            return <GenerateDataAndTestDialog {...props} />;
        case WindowKind.compareVersions:
            return <CompareVersionsDialog {...props} />;
        case WindowKind.customAction:
            return <CustomActionDialog {...props} />;
        case WindowKind.genericAction:
            return <GenericActionDialog {...props} />;
        case WindowKind.confirm:
            return <GenericConfirmDialog {...props} />;
        case WindowKind.inform:
            return <GenericInfoDialog {...props} />;
        case WindowKind.editNode:
            return <NodeDetails {...props} />;
        case WindowKind.viewNode:
            return <NodeDetails {...props} readOnly />;
        case WindowKind.survey:
            return <FrameDialog {...props} />;
        case WindowKind.scenarioDetails: {
            return <ScenarioDetailsDialog {...props} />;
        }
        default:
            return (
                <WindowContent {...props}>
                    <Debug data={props.data} />
                    <DebugButtons currentId={props.data.id} />
                </WindowContent>
            );
    }
};

export const ContentGetter: React.FC<WindowContentProps<WindowKind>> = (props) => <NuThemeProvider>{contentGetter(props)}</NuThemeProvider>;
