import type { AdhocTestingButtonProps } from "../../toolbars/test/buttons/AdhocTestingButton";
import type { ScenarioTestButtonProps } from "../../toolbars/test/buttons/ScenarioTestButton";
import type { BuiltinButtonTypes, CustomButtonTypes } from "./buttonsMap";
import type { LinkButtonProps } from "./LinkButton";

type GenericButton<T, P = unknown> = {
    type: T;
    disabled?: boolean;
} & P;

type Button =
    | GenericButton<BuiltinButtonTypes>
    | GenericButton<CustomButtonTypes.customLink, LinkButtonProps>
    | GenericButton<CustomButtonTypes.adhocTesting, AdhocTestingButtonProps>
    | GenericButton<CustomButtonTypes.scenarioTest, ScenarioTestButtonProps>;

export type ToolbarButtonTypes = Button["type"];
export type ToolbarButton = Button;
export type PropsOfButton<T> = ToolbarButton & {
    type: T;
};
