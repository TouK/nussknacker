import React, { Component } from 'react'
import { render } from 'react-dom'
import '../stylesheets/spinner.styl'

export default class LoaderSpinner extends React.Component {

    render () {
      return (
        <div className='preloader-spin'>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
          <div className='preloader-spin__petal'></div>
        </div>
      );
    }
}
