import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import styles from '../styles/DetailPage.module.css';
import { getProfile } from '../api/moabomApi';
import { HOUSEHOLD_OPTIONS, SPECIAL_OPTIONS, labelText } from '../api/policyLabels';
import { useAuth } from '../auth/AuthContext';

const ProfilePage = () => {
  const navigate = useNavigate();
  const { user, token, logout } = useAuth();
  const [profile, setProfile] = useState(null);
  const [message, setMessage] = useState('프로필을 불러오는 중입니다.');

  useEffect(() => {
    if (!user || !token) {
      return undefined;
    }
    let active = true;
    getProfile(user.id, token)
      .then((loaded) => {
        if (!active) {
          return;
        }
        setProfile(loaded);
        setMessage('');
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        setProfile(null);
        setMessage(error.status === 404
          ? '저장된 추천 프로필이 없습니다. 프로필 입력 화면에서 연령, 지역, 소득분위를 저장해 주세요.'
          : error.message);
      });
    return () => {
      active = false;
    };
  }, [token, user]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className={styles.container}>
      <div className={styles.contentWrapper}>
        <h1 className={styles.mainTitle}>마이페이지</h1>
        <div className={styles.detailCard}>
          <p className={styles.sectionContent}>사용자 아이디: {user.loginId}</p>
          <p className={styles.sectionContent}>이름: {user.name}</p>
          <p className={styles.sectionContent}>이메일: {user.email}</p>
          {profile && (
            <>
              <p className={styles.sectionContent}>연령: {profile.age}세</p>
              <p className={styles.sectionContent}>거주지역: {profile.region}</p>
              <p className={styles.sectionContent}>소득분위: {profile.incomeBracket}분위</p>
              <p className={styles.sectionContent}>가구 형태: {labelText(HOUSEHOLD_OPTIONS, profile.householdTypes)}</p>
              <p className={styles.sectionContent}>특수조건: {labelText(SPECIAL_OPTIONS, profile.specialConditions)}</p>
              <p className={styles.sectionContent}>관심 분야: {(profile.interests || []).join(', ') || '선택 없음'}</p>
            </>
          )}
          {message && <p className={styles.sectionContent}>{message}</p>}
          <Link to="/register" className={styles.sectionContentLink}>프로필 입력 및 수정</Link>
          <p className={styles.sectionContentLink} onClick={handleLogout}>로그아웃</p>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
