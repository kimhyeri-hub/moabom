// src/components/SecondaryButton.jsx

import React from 'react';
import styles from '../styles/Button.module.css';

const SecondaryButton = ({ children, ...props }) => {
  return (
    <button className={`${styles.button} ${styles.secondary}`} {...props}>
      {children}
    </button>
  );
};

export default SecondaryButton;