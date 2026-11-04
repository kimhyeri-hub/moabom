import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import styles from '../styles/HomePage.module.css';
import MainBannerImage from '../assets/images/main_banner.jpg';
import { getGrants, getRecommendations } from '../api/moabomApi';
import { useAuth } from '../auth/AuthContext';

const SCROLL_AMOUNT = 400;

const statusLabel = {
  ELIGIBLE: '신청 가능',
  CHECK_REQUIRED: '조건 확인 필요',
  INELIGIBLE: '자격 미충족',
};

function recommendationDetail(item) {
  const reasons = item.reasons || [];
  const reason = reasons.find((entry) => entry.includes(' - ')) || item.grant.description;
  return reason
    .replace(' PASS - ', ' 충족 - ')
    .replace(' FAIL - ', ' 불충족 - ')
    .replace(' UNKNOWN - ', ' 정보 부족 - ');
}

const HomePage = () => {
  const navigate = useNavigate();
  const { ready, user, token } = useAuth();
  const [params] = useSearchParams();
  const query = params.get('q') || '';
  const popularRef = useRef(null);
  const recommendRef = useRef(null);
  const recommendAnchorRef = useRef(null);
  const [grants, setGrants] = useState([]);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recommendationMessage, setRecommendationMessage] = useState('');

  useEffect(() => {
    if (!ready) {
      return undefined;
    }
    let active = true;
    setLoading(true);
    const recommendationCall = user
      ? getRecommendations(user.id, token)
      : Promise.resolve(null);
    Promise.allSettled([getGrants(), recommendationCall])
      .then(([grantResult, recommendationResult]) => {
        if (!active) {
          return;
        }
        if (grantResult.status === 'fulfilled') {
          setGrants(grantResult.value.grants || []);
        } else {
          setError(grantResult.reason.message);
        }
        if (!user) {
          setRecommendations([]);
          setRecommendationMessage('로그인하면 본인 조건에 맞는 추천을 볼 수 있습니다.');
        } else if (recommendationResult.status === 'fulfilled') {
          setRecommendations(recommendationResult.value.recommendations || []);
          setRecommendationMessage('');
        } else if (recommendationResult.reason.status === 404) {
          setRecommendations([]);
          setRecommendationMessage('프로필을 저장하면 맞춤 추천이 표시됩니다.');
        } else if (grantResult.status === 'fulfilled') {
          setRecommendationMessage(recommendationResult.reason.message);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [ready, user, token]);

  const visibleGrants = useMemo(() => {
    const keyword = query.trim();
    if (!keyword) {
      return grants;
    }
    return grants.filter((grant) => `${grant.title} ${grant.description}`.includes(keyword));
  }, [grants, query]);

  const handleScroll = (ref, scrollOffset) => {
    if (ref.current) {
      ref.current.scrollBy({ left: scrollOffset, behavior: 'smooth' });
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.mainBanner} style={{ backgroundImage: `url(${MainBannerImage})` }}>
        <div className={styles.bannerWrapper}>
          <div className={styles.bannerContent}>
            <h1 className={styles.bannerTitle}>나의 지원금</h1>
            <h1 className={styles.bannerTitle}>여기 다 모아봄</h1>
            <div className={styles.bannerButtons}>
              <button className={styles.supportButton} onClick={() => popularRef.current?.scrollIntoView({ behavior: 'smooth' })}>
                지원금 바로보기
              </button>
              {user ? (
                <button type="button" className={styles.loginButton} onClick={() => recommendAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
                  맞춤 지원금 추천받기
                </button>
              ) : (
                <button type="button" className={styles.loginButton} onClick={() => navigate('/login')}>로그인</button>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className={styles.contentArea}>
        {error && <p className={styles.statusLine}>{error}</p>}
        {loading && <p className={styles.statusLine}>지원금을 불러오는 중입니다.</p>}

        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>확인된 지원금 살펴보기</h2>
          <div className={styles.scrollControls}>
            <button className={styles.scrollButton} onClick={() => handleScroll(popularRef, -SCROLL_AMOUNT)} aria-label="이전 지원금 보기">&lt;</button>
            <button className={styles.scrollButton} onClick={() => handleScroll(popularRef, SCROLL_AMOUNT)} aria-label="다음 지원금 보기">&gt;</button>
          </div>
        </div>

        <div className={styles.gridSection} ref={popularRef}>
          {!loading && visibleGrants.length === 0 && <p className={styles.statusLine}>표시할 지원금이 없습니다.</p>}
          {visibleGrants.map((grant) => (
            <Link key={grant.grantId} to={`/support/details/${grant.grantId}`} className={styles.card}>
              <div className={styles.cardImageContainer} />
              <div className={styles.cardContent}>
                <div className={styles.cardCategory}>{grant.categoryTitle} · {grant.sample ? '예시' : '공개자료'}</div>
                <div className={styles.cardTitle}>{grant.title}</div>
                <div className={styles.cardDetail}>{grant.description}</div>
              </div>
            </Link>
          ))}
        </div>

        <h2 className={styles.sectionTitle}>모아챗 바로가기 &gt;</h2>
        <Link to="/moachat" className={styles.fullWidthCard}></Link>

        <div className={styles.sectionHeader} ref={recommendAnchorRef}>
          <h2 className={styles.sectionTitle}>모아가 추천해요!</h2>
          <div className={styles.scrollControls}>
            <button className={styles.scrollButton} onClick={() => handleScroll(recommendRef, -SCROLL_AMOUNT)} aria-label="이전 추천 보기">&lt;</button>
            <button className={styles.scrollButton} onClick={() => handleScroll(recommendRef, SCROLL_AMOUNT)} aria-label="다음 추천 보기">&gt;</button>
          </div>
        </div>
        {recommendationMessage && <p className={styles.statusLine}>{recommendationMessage}</p>}
        <div className={styles.gridSection} ref={recommendRef}>
          {!loading && recommendations.length === 0 && !recommendationMessage && (
            <p className={styles.statusLine}>조건에 맞는 추천 지원금이 없습니다.</p>
          )}
          {recommendations.map((item) => (
            <Link key={item.grant.grantId} to={`/support/details/${item.grant.grantId}`} className={styles.card}>
              <div className={styles.cardImageContainer} />
              <div className={styles.cardContent}>
                <div className={styles.cardCategory}>
                  {item.rank}위 · 점수 {item.score} · {statusLabel[item.eligibilityStatus] || item.eligibilityStatus} · {item.grant.sample ? '예시' : '공개자료'}
                </div>
                <div className={styles.cardTitle}>{item.grant.title}</div>
                <div className={styles.cardDetail}>{recommendationDetail(item)}</div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
};

export default HomePage;
