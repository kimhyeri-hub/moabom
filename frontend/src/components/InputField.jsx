// src/components/InputField.jsx

import React from 'react';
import styles from '../styles/InputField.module.css';

const InputField = ({ type, placeholder, ...props }) => {
  return (
    <input
      className={styles.input}
      type={type}
      placeholder={placeholder}
      {...props}
    />
  );
};

export default InputField;