import React, { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import styles from './HeaderAccount.module.css';

function HeaderAccount() {
  const { ready, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const buttonRef = useRef(null);
  const menuId = useId();
  const loginId = user?.loginId || user?.username || '';

  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const closeOnOutside = (event) => {
      if (!rootRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', closeOnOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  const moveMenuFocus = (current, direction) => {
    const items = [...rootRef.current.querySelectorAll('[role="menuitem"]')];
    const index = items.indexOf(current);
    const next = items[(index + direction + items.length) % items.length];
    next?.focus();
  };

  const handleLogout = async () => {
    setOpen(false);
    await logout();
    navigate('/');
  };

  if (!ready) {
    return <span className={styles.account} aria-hidden="true" />;
  }

  if (!user) {
    return (
      <Link to="/login" className={styles.guestLink}>
        로그인 / 회원가입
      </Link>
    );
  }

  return (
    <div className={styles.account} ref={rootRef}>
      <button
        type="button"
        ref={buttonRef}
        className={styles.accountButton}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={`${loginId} 계정 메뉴`}
        onClick={() => setOpen((current) => !current)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault();
            setOpen(true);
            window.requestAnimationFrame(() => {
              rootRef.current?.querySelector('[role="menuitem"]')?.focus();
            });
          }
        }}
      >
        <svg className={styles.icon} viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="8" cy="5.5" r="2.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
          <path d="M3.2 13.2c.8-2.2 2.5-3.2 4.8-3.2s4 1 4.8 3.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
        </svg>
        <span className={styles.loginId}>{loginId}</span>
        <svg className={`${styles.icon} ${styles.chevron}`} viewBox="0 0 12 12" aria-hidden="true">
          <path d="M2.2 4.2L6 8l3.8-3.8" fill="none" stroke="currentColor" strokeWidth="1.4" />
        </svg>
      </button>
      {open ? (
        <ul
          className={styles.menu}
          id={menuId}
          role="menu"
          aria-label="계정 메뉴"
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              moveMenuFocus(event.target, 1);
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              moveMenuFocus(event.target, -1);
            }
          }}
        >
          <li role="none">
            <Link
              to="/mypage"
              className={styles.menuItem}
              role="menuitem"
              onClick={() => setOpen(false)}
            >
              마이페이지
            </Link>
          </li>
          <li role="none">
            <button type="button" className={styles.menuItem} role="menuitem" onClick={handleLogout}>
              로그아웃
            </button>
          </li>
        </ul>
      ) : null}
    </div>
  );
}

export default HeaderAccount;
