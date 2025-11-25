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
      <nav className={styles.nav}>
        <span className={styles.navItem}>마이페이지</span>
        <span className={styles.navItem}>알림</span>
        <span className={styles.navItem}>모아챗</span>
      </nav>
    </header>
  );
};

export default Header;