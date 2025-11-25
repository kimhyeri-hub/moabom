// src/pages/RegisterPage.jsx

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header'; 
import styles from '../styles/RegisterPage.module.css';
import InputField from '../components/InputField'; 

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

  // ⭐ 상태를 배열로 초기화하여 다중 선택 허용 ⭐
  const [selectedTags, setSelectedTags] = useState({
      연령대: [],
      가구형태: [],
      소득분위: [],
      지역: [],
      특수조건: []
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

  const handleRegisterSubmit = (e) => {
    e.preventDefault();
    console.log('회원가입 정보 제출', selectedTags); 
    alert('회원가입 완료! (실제 로직 구현 필요)');
    // ⭐ 메인 페이지로 이동 ⭐
    navigate('/'); 
  };

  return (
    <div className={styles.container}>
      <Header />
      
      <div className={styles.content}>
        <h1 className={styles.logo}>MOABOM</h1>
        <p className={styles.subtitle}>회원 정보를 입력해주세요.</p>

        <form className={styles.form} onSubmit={handleRegisterSubmit}>
          
          {/* ----- 기본 정보 섹션 (입력 필드) ----- */}
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 이름</label>
            <InputField type="text" fullWidth={true} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 아이디</label>
            <div className={styles.idInputContainer}>
              <InputField type="text" /> 
              <button type="button" className={styles.checkButton}>중복 확인</button>
            </div>
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 비밀번호</label>
            <InputField type="password" fullWidth={true} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 생년월일</label>
            <InputField type="text" placeholder="YYYYMMDD" fullWidth={true} />
          </div>
          
          <div className={styles.inputGroup}>
            <label><span className={styles.required}>*</span> 이메일</label>
            <InputField type="email" fullWidth={true} />
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
              {['1인 가구', '신혼 부부', '다자녀', '한부모'].map((tag) => (
                <TagButton 
                    key={tag} 
                    isSelected={selectedTags.가구형태.includes(tag)} 
                    onClick={() => handleTagClick('가구형태', tag)}
                >
                    {tag}
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
              {['서울', '경기', '인천', '부산', '기타'].map((tag) => (
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
          
          {/* 특수조건 섹션 */}
          <div className={styles.buttonSection}>
            <label className={styles.tagLabel}>특수조건</label>
            <div className={styles.buttonRow}>
              {['장애인', '국가유공자', '신혼', '다자녀', '기타'].map((tag) => (
                <TagButton 
                    key={tag} 
                    isSelected={selectedTags.특수조건.includes(tag)}
                    onClick={() => handleTagClick('특수조건', tag)}
                >
                    {tag}
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

        </form>
      </div>
    </div>
  );
};

export default RegisterPage;