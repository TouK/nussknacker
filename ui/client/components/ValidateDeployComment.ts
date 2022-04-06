import {isEmpty} from "lodash"
import {FeaturesSettings} from "../actions/nk"

interface CommentValidation {
  isValid: boolean,
  toolTip?: string,
}

export default (comment: string, {
  validationPattern,
}: FeaturesSettings["commentSettings"] & FeaturesSettings["deploySettings"]): CommentValidation => {
  let validated: CommentValidation = {isValid: true}
  //
  // if (!isEmpty(validationPattern) && isEmpty(comment)) {
  //   validated = {isValid: false, toolTip: "Comment is required."}
  // } else if (!isEmpty(validationPattern)) {
  //   const match = comment.match(new RegExp(validationPattern, "g"))
  //   if (!match) {
  //     validated = {isValid: false, toolTip: "Comment does not match required pattern."}
  //   }
  // }

  //validation takes place in backend from now on, above to be removed

  return validated
}

