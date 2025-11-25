import React, { useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import styles from '../styles/HomePage.module.css'; 

// 이미지를 src/assets/images 폴더에서 import 합니다.
import MainBannerImage from '../assets/images/main_banner.jpg'; 

const HomePage = () => {
  const navigate = useNavigate();
  
  // ⭐ 1. 스크롤할 요소를 지정할 두 개의 Ref 생성 ⭐
  const popularRef = useRef(null); // 실시간 인기 지원금 Ref
  const recommendRef = useRef(null); // 모아가 추천해요 Ref

  const handleLoginClick = () => {
    navigate('/login');
  };

  // 스크롤 이동량 (CSS에서 카드 너비가 뷰포트에 상대적으로 설정되었으므로 고정값을 사용)
  const SCROLL_AMOUNT = 400; 

  // ⭐ 2. Ref를 인수로 받는 가로 스크롤 핸들러 함수 ⭐
  const handleScroll = (ref, scrollOffset) => {
    if (ref.current) {
      ref.current.scrollBy({
        left: scrollOffset, 
        behavior: 'smooth' 
      });
    }
  };

  return (
    <div className={styles.container}>
      
      {/* 1. 상단 헤더 메뉴 (400px 균일 간격 + 중앙 정렬) */}
      <header className={styles.header}>
        <div className={styles.headerWrapper}>
          <div className={styles.logo}>MOABOM</div>
          <nav className={styles.nav}>
            <Link to="/mypage" className={styles.navLink}>마이페이지</Link>
            <Link to="/alerts" className={styles.navLink}>알림</Link>
            <Link to="/moachat" className={styles.navLink}>모아챗</Link>
            <Link to="/login" className={styles.navLink}>회원가입</Link>
          </nav>
        </div>
      </header>

      {/* 2. 메인 배너 섹션 */}
      <div 
        className={styles.mainBanner}
        style={{ backgroundImage: `url(${MainBannerImage})` }}
      >
        <div className={styles.bannerWrapper}> 
            <div className={styles.bannerContent}>
              <h1 className={styles.bannerTitle}>나의 지원금</h1>
              <h1 className={styles.bannerTitle}>여기 다 모아봄</h1>
              <div className={styles.bannerButtons}>
                <button className={styles.supportButton}>지원금 바로보기</button>
                <button className={styles.loginButton} onClick={handleLoginClick}>로그인</button>
              </div>
            </div>
        </div>
      </div>

      {/* 3. 메인 콘텐츠 영역 */}
      <div className={styles.contentArea}>
        
        {/* 3.1 검색창 (CSS에서 넓고 둥글게 디자인됨) */}
        <div className={styles.searchBarContainer}>
          <input type="text" placeholder="검색어를 입력하세요" className={styles.searchBarInput} />
        </div>

        {/* 3.2 ⭐ 실시간 인기 지원금 섹션 (스크롤 및 Link 적용) ⭐ */}
        <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>실시간 인기 지원금 살펴보기</h2>
            <div className={styles.scrollControls}>
                <button 
                    className={styles.scrollButton} 
                    onClick={() => handleScroll(popularRef, -SCROLL_AMOUNT)} 
                    aria-label="이전 지원금 보기"
                >
                    &lt;
                </button>
                <button 
                    className={styles.scrollButton} 
                    onClick={() => handleScroll(popularRef, SCROLL_AMOUNT)} 
                    aria-label="다음 지원금 보기"
                >
                    &gt;
                </button>
            </div>
        </div>
        
        {/* popularRef 연결 */}
        <div className={styles.gridSection} ref={popularRef}>
          {/* Link로 감싸서 클릭 가능하게 변경 */}
          <Link to="/support/details/popular/1" className={styles.card}>
            인기 지원금 1
          </Link>
          <Link to="/support/details/popular/2" className={styles.card}>
            인기 지원금 2
          </Link>
          <Link to="/support/details/popular/3" className={styles.card}>
            인기 지원금 3
          </Link>
          <Link to="/support/details/popular/4" className={styles.card}>
            인기 지원금 4
          </Link>
          <Link to="/support/details/popular/5" className={styles.card}>
            인기 지원금 5
          </Link>
        </div>

        {/* 3.3 모아챗 바로가기 섹션 */}
        <h2 className={styles.sectionTitle}>모아챗 바로가기 &gt;</h2>
        <Link to="/moachat" className={styles.fullWidthCard}></Link>


        {/* 3.4 ⭐ 모아가 추천해요! 섹션 (스크롤 및 Link 적용) ⭐ */}
        <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>모아가 추천해요!</h2>
            <div className={styles.scrollControls}>
                <button 
                    className={styles.scrollButton} 
                    onClick={() => handleScroll(recommendRef, -SCROLL_AMOUNT)} 
                    aria-label="이전 추천 보기"
                >
                    &lt;
                </button>
                <button 
                    className={styles.scrollButton} 
                    onClick={() => handleScroll(recommendRef, SCROLL_AMOUNT)} 
                    aria-label="다음 추천 보기"
                >
                    &gt;
                </button>
            </div>
        </div>
        
        {/* recommendRef 연결 */}
        <div className={styles.gridSection} ref={recommendRef}>
          {/* Link로 감싸서 클릭 가능하게 변경 */}
          <Link to="/support/details/recommend/A" className={styles.card}>
            추천 카드 A
          </Link>
          <Link to="/support/details/recommend/B" className={styles.card}>
            추천 카드 B
          </Link>
          <Link to="/support/details/recommend/C" className={styles.card}>
            추천 카드 C
          </Link>
          <Link to="/support/details/recommend/D" className={styles.card}>
            추천 카드 D
          </Link>
          <Link to="/support/details/recommend/E" className={styles.card}>
            추천 카드 E
          </Link>
        </div>
        
      </div>
    </div>
  );
};

export default HomePage;