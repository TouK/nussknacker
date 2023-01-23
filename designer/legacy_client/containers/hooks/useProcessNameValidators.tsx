import {useMemo} from "react"
import * as DialogMessages from "../../common/DialogMessages"
import {HandledErrorType, mandatoryValueValidator, Validator, ValidatorType} from "../../components/graph/node-modal/editors/Validators"
import {useClashedNames} from "./useClashedNames"

//TODO: move this validation to backend to simplify FE code
export function useProcessNameValidators(): Validator[] {
  const clashedNames = useClashedNames(true)

  const alreadyExistsValidator = useMemo((): Validator => ({
    isValid: (name) => !clashedNames.includes(name),
    message: DialogMessages.valueAlreadyTaken,
    description: DialogMessages.valueAlreadyTakenDescription,
    validatorType: ValidatorType.Frontend,
    handledErrorType: HandledErrorType.AlreadyExists,
  }), [clashedNames])

  return useMemo(
    () => [
      mandatoryValueValidator,
      alreadyExistsValidator,
    ],
    [alreadyExistsValidator],
  )
}
