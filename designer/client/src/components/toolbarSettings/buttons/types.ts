import { ActionButtonProps } from "./ActionButton";
import { BuiltinButtonTypes } from "./BuiltinButtonTypes";
import { CustomButtonTypes } from "./CustomButtonTypes";
import { LinkButtonProps } from "./LinkButton";
import { AdhocTestingButtonProps } from "../../toolbars/test/buttons/AdhocTestingButton";

type GenericButton<T, P = unknown> = {
    type: T;
    disabled?: boolean;
} & P;

type Button =
    | GenericButton<BuiltinButtonTypes>
    | GenericButton<CustomButtonTypes.customAction, ActionButtonProps>
    | GenericButton<CustomButtonTypes.customLink, LinkButtonProps>
    | GenericButton<CustomButtonTypes.adhocTesting, AdhocTestingButtonProps>;

export type ToolbarButtonTypes = Button["type"];
export type ToolbarButton = Button;
