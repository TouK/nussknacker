import {isEmpty} from "lodash"
import {FeaturesSettings} from "../actions/nk"

interface CommentValidation {
  isValid: boolean,
  toolTip?: string,
}

export default (comment: string, {
  requireComment,
  matchExpression,
}: FeaturesSettings["commentSettings"] & FeaturesSettings["deploySettings"]): CommentValidation => {
  let validated: CommentValidation = {isValid: true}

  if (requireComment && isEmpty(comment)) {
    validated = {isValid: false, toolTip: "Comment is required."}
  } else if (requireComment && !isEmpty(matchExpression)) {
    const match = comment.match(new RegExp(matchExpression, "g"))
    if (!match) {
      validated = {isValid: false, toolTip: "Comment does not match required pattern."}
    }
  }

  return validated
}

