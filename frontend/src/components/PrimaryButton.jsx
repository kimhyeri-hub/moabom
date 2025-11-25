// src/components/PrimaryButton.jsx

import React from 'react';
import styles from '../styles/Button.module.css';

const PrimaryButton = ({ children, ...props }) => {
  return (
    <button className={`${styles.button} ${styles.primary}`} {...props}>
      {children}
    </button>
  );
};

export default PrimaryButton;