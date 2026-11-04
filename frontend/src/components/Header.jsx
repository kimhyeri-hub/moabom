// src/components/Header.jsx

import React from 'react';
import styles from '../styles/Header.module.css';
import { ChevronLeft } from 'lucide-react'; 

const Header = () => {
  return (
    <header className={styles.header}>
      <div className={styles.left}>
        {/* 뒤로 가기 아이콘 (lucide-react 라이브러리 가정) */}
        <ChevronLeft size={24} color="#888" /> 
      </div>
    </header>
  );
};

export default Header;