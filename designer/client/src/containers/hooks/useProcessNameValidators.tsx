import { useMemo } from "react";

import DialogMessages from "../../common/DialogMessages";
import type { Validator } from "../../components/graph/node-modal/editors/Validators";
import { HandledErrorType, mandatoryValueValidator } from "../../components/graph/node-modal/editors/Validators";
import { useClashedNames } from "./useClashedNames";

//TODO: move this validation to backend to simplify FE code
export function useProcessNameValidators(): Validator[] {
    const clashedNames = useClashedNames();

    const alreadyExistsValidator = useMemo(
        (): Validator => ({
            isValid: (name) => !clashedNames.includes(name),
            message: DialogMessages.valueAlreadyTaken,
            description: DialogMessages.valueAlreadyTakenDescription,
            handledErrorType: HandledErrorType.AlreadyExists,
        }),
        [clashedNames],
    );

    return useMemo(() => [mandatoryValueValidator, alreadyExistsValidator], [alreadyExistsValidator]);
}
