import React from 'react'
import Enzyme, {mount} from 'enzyme'
import {errorValidator, mandatoryValueValidator} from "../components/graph/node-modal/editors/Validators"
import ValidationLabels from "../components/modals/ValidationLabels"
import Adapter from 'enzyme-adapter-react-16'

describe("displaying validation labels", () => {
  Enzyme.configure({ adapter: new Adapter() })

  it("display only fe validation when both be and fe validations available for the same error type", () => {
    //given
    const fieldName = "fieldName"
    const emptyValue = ""
    const backendError = {message: "Test", description: "test", typ: "EmptyMandatoryParameter", fieldName: "fieldName"}
    const validators = [
      mandatoryValueValidator,
      errorValidator([backendError], "fieldName")
    ]

    //when
    const validationLabels = mount(<ValidationLabels validators={validators} values={[emptyValue]}/>)

    //then
    expect(validationLabels.find('.validation-label').length).toBe(1)
  })

  it("display validations for different error type", () => {
    //given
    const fieldName = "fieldName"
    const emptyValue = ""
    const backendError = {message: "Test", description: "test", typ: "AnotherErrorType", fieldName: "fieldName"}
    const validators = [
      mandatoryValueValidator,
      errorValidator([backendError], "fieldName")
    ]

    //when
    const validationLabels = mount(<ValidationLabels validators={validators} values={[emptyValue]}/>)

    //then
    expect(validationLabels.find('.validation-label').length).toBe(2)
  })

})
