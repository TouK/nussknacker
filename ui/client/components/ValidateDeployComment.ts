import {isEmpty} from "lodash"
import {FeaturesSettings} from "../actions/nk"

interface CommentValidation {
  isValid: boolean,
  toolTip?: string,
}

export default (comment: string, {
  validationPattern,
  substitutionPattern,
}: FeaturesSettings["commentSettings"] & FeaturesSettings["deploySettings"]): CommentValidation => {
  let validated: CommentValidation = {isValid: true}

  if (!isEmpty(validationPattern) && isEmpty(comment)) {
    validated = {isValid: false, toolTip: "Comment is required."}
  } else if (!isEmpty(validationPattern) && !isEmpty(substitutionPattern)) {
    const match = comment.match(new RegExp(substitutionPattern, "g"))
    if (!match) {
      validated = {isValid: false, toolTip: "Comment does not match required pattern."}
    }
  }

  return validated
}

