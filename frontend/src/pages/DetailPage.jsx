import React from 'react';
import { useParams, Link } from 'react-router-dom';
import styles from '../styles/DetailPage.module.css'; 

const DetailPage = () => {
  // URL에서 지원금 ID를 가져옵니다. 
  const { id } = useParams();

  // 데모 데이터 (applyLink 수정)
  const supportData = {
    title: "00-001. 지원금 제목",
    content: "지원금 상세 내용입니다. 지원금 상세 내용입니다. 테스트 화면임 지원금 상세 내용입니다. 지원금 상세 내용입니다. 테스트 화면임 지원금 상세 내용입니다. 지원금 상세 내용입니다. 테스트 화면임 지원금 상세 내용입니다. 지원금 상세 내용입니다. 테스트 화면임",
    region: "경기도",
    age: "청년층 (만 24세 ~ 만 39세)",
    period: "25년 10월 17일 ~ 12월 28일",
    agency: "보건복지부",
    // ⭐ 요청하신 URL로 변경 ⭐
    applyLink: "https://www.gg.go.kr/contents/contents.do?ciIdx=1037&menuId=2736", 
    dynamicTitle: id ? `[${id}] 지원금 상세 정보` : "지원금 상세 정보",
  };
  
  // URL 표시를 위한 텍스트와 실제 링크를 분리합니다.
  const displayLinkText = "신청 바로가기 (경기도청)";


  // 신청 바로가기 클릭 핸들러 (새 탭에서 열기)
  const handleApplyClick = () => {
    // URL이 유효한지 간단히 확인 후 새 탭에서 엽니다.
    if (supportData.applyLink) {
      window.open(supportData.applyLink, '_blank');
    }
  };


  return (
    <div className={styles.container}>
      
      {/* 1. 상단 헤더 메뉴 (HomePage와 동일한 구조) */}
      <header className={styles.header}>
        <div className={styles.headerWrapper}> 
          
          <Link to="/" className={styles.logo}>MOABOM</Link> 
          
          <nav className={styles.nav}>
            <Link to="/mypage" className={styles.navLink}>마이페이지</Link>
            <Link to="/alerts" className={styles.navLink}>알림</Link>
            <Link to="/moachat" className={styles.navLink}>모아챗</Link>
            <Link to="/login" className={styles.navLink}>로그인/회원가입</Link> 
          </nav>
        </div>
      </header>
      
      {/* 2. 메인 콘텐츠 영역 */}
      <div className={styles.contentWrapper}>
        
        {/* 지원금 제목 섹션 */}
        <h1 className={styles.mainTitle}>{supportData.dynamicTitle}</h1>
        
        {/* 포스터 영역 */}
        <div className={styles.posterContainer}>
          {/* 포스터 이미지 삽입 시 여기에 <img src="..." />를 넣습니다. */}
        </div>
        
        <div className={styles.detailCard}>
            
            {/* 지원금 상세 내용 */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>지원금 제목</h2>
                <p className={styles.sectionContent}>{supportData.content}</p>
            </div>

            <div className={styles.separator}></div>
            
            {/* 대상 지역 */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>대상 지역</h2>
                <p className={styles.sectionContent}>{supportData.region}</p>
            </div>
            
            {/* 대상 연령대 */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>대상 연령대</h2>
                <p className={styles.sectionContent}>{supportData.age}</p>
            </div>

            {/* 신청 기간 */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>신청 기간</h2>
                <p className={styles.sectionContent}>{supportData.period}</p>
            </div>
            
            {/* 정책 시행 기관 */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>정책 시행 기관</h2>
                <p className={styles.sectionContent}>{supportData.agency}</p>
            </div>

            {/* ⭐ 신청 바로가기 섹션 수정: 클릭 이벤트 추가 ⭐ */}
            <div className={styles.section}>
                <h2 className={styles.sectionTitle}>신청 바로가기</h2>
                {/* <a> 태그 대신 <p> 태그를 사용하고 클릭 이벤트를 연결합니다. */}
                <p 
                    className={styles.sectionContentLink} 
                    onClick={handleApplyClick}
                    title={supportData.applyLink} // 마우스를 올리면 실제 URL이 보이게 합니다.
                >
                    {displayLinkText}
                </p>
            </div>
            
        </div>
        
        {/* 하단 스크롤 버튼 */}
        <div className={styles.bottomControls}>
            <button className={styles.controlButton}>&lt;</button>
            <button className={styles.controlButton}>&gt;</button>
        </div>
        
      </div>
    </div>
  );
};

export default DetailPage;