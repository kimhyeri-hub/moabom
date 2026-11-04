// src/pages/RegisterPage.jsx

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header'; 
import styles from '../styles/RegisterPage.module.css';
import InputField from '../components/InputField';
import { getProfile, saveProfile } from '../api/moabomApi';
import { checkLoginId, signup, updateAccount } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';
import { HOUSEHOLD_OPTIONS, SPECIAL_OPTIONS, codesFor, labelsFor } from '../api/policyLabels'; 

// 임시 버튼 컴포넌트 (type="button"으로 폼 제출 방지)
const TagButton = ({ children, isSelected, onClick }) => (
  <button
    type="button"
    className={`${styles.tagButton} ${isSelected ? styles.selectedTag : ''}`}
    onClick={onClick}
  >
    {children}
  </button>
);


const RegisterPage = () => {
  const navigate = useNavigate();
  const { user, token, setSession } = useAuth();
  const [account, setAccount] = useState({
    name: '',
    loginId: '',
    password: '',
    dateOfBirth: '',
    email: '',
  });

  const updateField = (field) => (event) => {
    setAccount((current) => ({ ...current, [field]: event.target.value }));
  };

  // ⭐ 상태를 배열로 초기화하여 다중 선택 허용 ⭐
  const [selectedTags, setSelectedTags] = useState({
      연령대: [],
      가구형태: [],
      소득분위: [],
      지역: [],
      특수조건: [],
      관심분야: [],
  });

  const handleTagClick = (group, value) => {
      setSelectedTags(prev => {
          const currentArray = prev[group] || [];
          
          if (currentArray.includes(value)) {
              // 제거 (해제)
              return {
                  ...prev,
                  [group]: currentArray.filter(tag => tag !== value)
              };
          } else {
              // 추가 (선택)
              return {
                  ...prev,
                  [group]: [...currentArray, value]
              };
          }
      });
  };

  const handleGoBack = () => {
    navigate(-1);
  };

  const [formMessage, setFormMessage] = useState('');

  useEffect(() => {
    if (user) {
      setAccount((current) => ({
        ...current,
        name: user.name || '',
        loginId: user.loginId || '',
        email: user.email || '',
        dateOfBirth: user.dateOfBirth ? user.dateOfBirth.replace(/-/g, '') : '',
      }));
    }
    if (!user || !token) {
      return undefined;
    }
    getProfile(user.id, token)
      .then((profile) => {
        const ageTag = profile.age >= 60 ? '60대 이상' : `${Math.floor(profile.age / 10)}0대`;
        const incomeTag = `${profile.incomeBracket}분위`;
        setSelectedTags((current) => ({
          ...current,
          연령대: ageTag ? [ageTag] : [],
          소득분위: incomeTag ? [incomeTag] : [],
          지역: profile.region ? [profile.region] : [],
          가구형태: labelsFor(HOUSEHOLD_OPTIONS, profile.householdTypes),
          특수조건: labelsFor(SPECIAL_OPTIONS, profile.specialConditions),
          관심분야: profile.interests || [],
        }));
      })
      .catch(() => {});
    return undefined;
  }, [token, user]);

  const handleCheckId = async () => {
    try {
      const result = await checkLoginId(account.loginId.trim());
      setFormMessage(result.available ? '사용할 수 있는 아이디입니다.' : '이미 사용 중인 아이디입니다.');
    } catch (error) {
      setFormMessage(error.message);
    }
  };

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    const ageMap = { '20대': 25, '30대': 35, '40대': 45, '50대': 55, '60대 이상': 65 };
    const age = ageMap[selectedTags.연령대[0]];
    const region = selectedTags.지역[0];
    const income = Number.parseInt(selectedTags.소득분위[0], 10);
    if (!age || !region || !income) {
      setFormMessage('추천에 사용할 연령대, 지역, 소득분위를 각각 하나 이상 선택해 주세요.');
      return;
    }
    const profile = {
      age,
      region,
      incomeBracket: income,
      householdTypes: codesFor(HOUSEHOLD_OPTIONS, selectedTags.가구형태),
      specialConditions: codesFor(SPECIAL_OPTIONS, selectedTags.특수조건),
      interests: selectedTags.관심분야,
    };
    try {
      let sessionUser = user;
      let sessionToken = token;
      if (!sessionUser) {
        if (!account.password) {
          setFormMessage('비밀번호를 입력해 주세요.');
          return;
        }
        const session = await signup({
          name: account.name.trim(),
          loginId: account.loginId.trim(),
          password: account.password,
          dateOfBirth: account.dateOfBirth.trim(),
          email: account.email.trim(),
          region,
          incomeBracket: income,
        });
        setSession(session.token, session.user);
        sessionUser = session.user;
        sessionToken = session.token;
      } else {
        const updated = await updateAccount(sessionToken, {
          name: account.name.trim(),
          email: account.email.trim(),
          dateOfBirth: account.dateOfBirth.trim(),
          region,
          incomeBracket: income,
        });
        setSession(sessionToken, updated.user);
      }
      await saveProfile(sessionUser.id, sessionToken, profile);
      setFormMessage('회원 정보와 추천 프로필을 저장했습니다.');
      navigate('/');
    } catch (error) {
      setFormMessage(error.message);
    }
  };

  return (
    <div className={styles.container}>
      <Header />
      
      <div className={styles.content}>
        <h1 className={styles.logo}>MOABOM</h1>
        <p className={styles.subtitle}>회원 정보를 입력해주세요. 연령대, 지역, 소득분위, 가구 형태, 특수조건이 추천 자격 검증에 사용됩니다.</p>

        <form className={styles.form} onSubmit={handleRegisterSubmit}>
          
          {/* ----- 기본 정보 섹션 (입력 필드) ----- */}
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 이름</label>
            <InputField type="text" fullWidth={true} value={account.name} onChange={updateField('name')} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 아이디</label>
            <div className={styles.idInputContainer}>
              <InputField type="text" value={account.loginId} onChange={updateField('loginId')} readOnly={Boolean(user)} /> 
              <button type="button" className={styles.checkButton} onClick={handleCheckId}>중복 확인</button>
            </div>
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 비밀번호</label>
            <InputField type="password" fullWidth={true} value={account.password} onChange={updateField('password')} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 생년월일</label>
            <InputField type="text" placeholder="YYYYMMDD" fullWidth={true} value={account.dateOfBirth} onChange={updateField('dateOfBirth')} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 이메일</label>
            <InputField type="email" fullWidth={true} value={account.email} onChange={updateField('email')} />
          </div>
          
          {/* ----- 추가 정보 섹션 (태그 버튼) ----- */}
          <h2 className={styles.sectionTitle}>추가 정보</h2>
          
          {/* 연령대 그룹 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>연령대</label>
            <div className={styles.buttonRow}>
              {['20대', '30대', '40대', '50대', '60대 이상'].map((tag) => (
                <TagButton 
                    key={tag} 
                    isSelected={selectedTags.연령대.includes(tag)} 
                    onClick={() => handleTagClick('연령대', tag)}
                >
                    {tag}
                </TagButton>
              ))}
            </div>
          </div>

          {/* 가구 형태 그룹 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>가구 형태</label>
            <div className={styles.buttonRow}>
              {HOUSEHOLD_OPTIONS.map((option) => (
                <TagButton
                    key={option.code}
                    isSelected={selectedTags.가구형태.includes(option.label)}
                    onClick={() => handleTagClick('가구형태', option.label)}
                >
                    {option.label}
                </TagButton>
              ))}
            </div>
          </div>
          
          {/* 소득 분위 그룹 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>소득 분위</label>
            <div className={styles.buttonRow}>
              {['1분위', '2분위', '3분위', '4분위', '5분위', '6분위'].map((tag) => (
                <TagButton 
                    key={tag} 
                    isSelected={selectedTags.소득분위.includes(tag)}
                    onClick={() => handleTagClick('소득분위', tag)}
                >
                    {tag}
                </TagButton>
              ))}
            </div>
          </div>
          
          {/* 지역 섹션 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>지역</label>
            <div className={styles.buttonRow}>
              {['예시특별시', '예시도', '전국(예시)'].map((tag) => (
                <TagButton 
                    key={tag} 
                    isSelected={selectedTags.지역.includes(tag)}
                    onClick={() => handleTagClick('지역', tag)}
                >
                    {tag}
                </TagButton>
              ))}
            </div>
          </div>
          
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>관심 분야</label>
            <div className={styles.buttonRow}>
              {['주거', '취업', '교육', '자산형성'].map((tag) => (
                <TagButton
                    key={tag}
                    isSelected={selectedTags.관심분야.includes(tag)}
                    onClick={() => handleTagClick('관심분야', tag)}
                >
                    {tag}
                </TagButton>
              ))}
            </div>
          </div>

          {/* 특수조건 섹션 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>특수조건</label>
            <div className={styles.buttonRow}>
              {SPECIAL_OPTIONS.map((option) => (
                <TagButton
                    key={option.code}
                    isSelected={selectedTags.특수조건.includes(option.label)}
                    onClick={() => handleTagClick('특수조건', option.label)}
                >
                    {option.label}
                </TagButton>
              ))}
            </div>
          </div>
          
          {/* ----- 하단 버튼 섹션 ----- */}
          <div className={styles.footerButtons}>
            <button type="button" className={`${styles.buttonBase} ${styles.backButton}`} onClick={handleGoBack}>
              돌아가기
            </button>
            <button type="submit" className={`${styles.buttonBase} ${styles.submitButton}`}>
              회원가입하기
            </button>
          </div>
          {formMessage && <p className={styles.subtitle}>{formMessage}</p>}

        </form>
      </div>
    </div>
  );
};

export default RegisterPage;