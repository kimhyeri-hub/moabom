import React from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import HeaderAccount from './HeaderAccount';
import styles from './SiteHeader.module.css';

function BellIcon() {
  return (
    <svg className={styles.icon} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3.6a5 5 0 0 0-5 5v2.1c0 .8-.3 1.5-.7 2.1L5 15.6h14l-1.3-2.8a3.4 3.4 0 0 1-.7-2.1V8.6a5 5 0 0 0-5-5z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M10 17.6a2 2 0 0 0 4 0" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function BookmarkIcon() {
  return (
    <svg className={styles.icon} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 4.5h10a1 1 0 0 1 1 1V20l-6-3.3L6 20V5.5a1 1 0 0 1 1-1z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
    </svg>
  );
}

function ChatIcon() {
  return (
    <svg className={styles.icon} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 6.2h14a1 1 0 0 1 1 1v7.2a1 1 0 0 1-1 1H10l-3.8 3.1V15.4H5a1 1 0 0 1-1-1V7.2a1 1 0 0 1 1-1z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
    </svg>
  );
}

function SiteHeader() {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') || '';

  const updateQuery = (value) => {
    const next = new URLSearchParams();
    if (value) {
      next.set('q', value);
    }
    if (location.pathname === '/') {
      setParams(next, { replace: true });
      return;
    }
    const search = next.toString();
    navigate({ pathname: '/', search: search ? `?${search}` : '' });
  };

  const toolClass = (path) => (
    location.pathname === path ? `${styles.tool} ${styles.toolCurrent}` : styles.tool
  );

  return (
    <header className={styles.header}>
      <div className={styles.bar}>
        <Link to="/" className={styles.logo}>MOABOM</Link>
        <form className={styles.searchForm} role="search" onSubmit={(event) => event.preventDefault()}>
          <input
            type="search"
            name="q"
            className={styles.searchInput}
            placeholder="검색어를 입력하세요"
            aria-label="지원금 검색"
            autoComplete="off"
            value={query}
            onChange={(event) => updateQuery(event.target.value)}
          />
        </form>
        <nav className={styles.tools} aria-label="주요 메뉴">
          <Link
            to="/alerts"
            className={toolClass('/alerts')}
            aria-label="알림"
            aria-current={location.pathname === '/alerts' ? 'page' : undefined}
          >
            <BellIcon />
            <span className={styles.toolLabel}>알림</span>
          </Link>
          <Link
            to={user ? '/scraps' : '/login'}
            className={toolClass('/scraps')}
            aria-label="스크랩"
            aria-current={location.pathname === '/scraps' ? 'page' : undefined}
          >
            <BookmarkIcon />
            <span className={styles.toolLabel}>스크랩</span>
          </Link>
          <Link
            to="/moachat"
            className={toolClass('/moachat')}
            aria-label="모아챗"
            aria-current={location.pathname === '/moachat' ? 'page' : undefined}
          >
            <ChatIcon />
            <span className={styles.toolLabel}>모아챗</span>
          </Link>
        </nav>
        <HeaderAccount />
      </div>
    </header>
  );
}

export default SiteHeader;
