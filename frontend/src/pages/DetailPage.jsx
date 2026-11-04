import React, { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import styles from '../styles/DetailPage.module.css';
import { cancelScrap, getBehaviors, getGrant, getRecommendations, recordEvent } from '../api/moabomApi';
import { useAuth } from '../auth/AuthContext';
import { HOUSEHOLD_OPTIONS, SPECIAL_OPTIONS, labelText } from '../api/policyLabels';

function conditionText(clarity, codes, options) {
  if (clarity === 'UNRESTRICTED') {
    return '제한 없음';
  }
  if (clarity === 'SPECIFIED') {
    return labelText(options, codes);
  }
  return '원문에 조건이 확인되지 않음';
}

function boundText(min, max, unit, missingLabel) {
  if (min == null && max == null) {
    return missingLabel;
  }
  if (min == null) {
    return `${max}${unit} 이하 · 하한은 원문에서 확인되지 않음`;
  }
  if (max == null) {
    return `${min}${unit} 이상 · 상한은 원문에서 확인되지 않음`;
  }
  return `${min}${unit} ~ ${max}${unit}`;
}

function periodText(grant) {
  if (!grant.applicationStart && !grant.endDate) {
    return '신청기간 확인 필요';
  }
  return `${grant.applicationStart || '시작일 없음'} ~ ${grant.endDate || '마감일 없음'}`;
}

function reasonText(reason) {
  return reason
    .replace('자격 검증: ELIGIBLE', '자격 검증: 신청 가능')
    .replace('자격 검증: CHECK_REQUIRED', '자격 검증: 조건 확인 필요')
    .replace('자격 검증: INELIGIBLE', '자격 검증: 자격 미충족')
    .replace(' PASS - ', ' 충족 - ')
    .replace(' FAIL - ', ' 불충족 - ')
    .replace(' UNKNOWN - ', ' 정보 부족 - ');
}

const statusLabel = {
  ELIGIBLE: '신청 가능',
  CHECK_REQUIRED: '조건 확인 필요',
  INELIGIBLE: '자격 미충족',
};

const DetailPage = () => {
  const { id } = useParams();
  const { user, token } = useAuth();
  const [grant, setGrant] = useState(null);
  const [recommendation, setRecommendation] = useState(null);
  const [scrapped, setScrapped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionMessage, setActionMessage] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    return getGrant(id)
      .then((loaded) => {
        setGrant(loaded);
        if (!user) {
          setScrapped(false);
          setRecommendation(null);
          return null;
        }
        return recordEvent(user.id, token, 'VIEW', loaded.grantId)
          .catch(() => null)
          .then(() => Promise.allSettled([
            getBehaviors(user.id, token),
            getRecommendations(user.id, token),
          ]));
      })
      .then((results) => {
        if (!results) {
          return;
        }
        const [behaviorResult, recommendationResult] = results;
        if (behaviorResult.status === 'fulfilled') {
          const behaviors = behaviorResult.value.behaviors || [];
          setScrapped(behaviors.some((behavior) => behavior.type === 'SCRAP' && behavior.grantId === id));
        }
        if (recommendationResult.status === 'fulfilled') {
          const matched = (recommendationResult.value.recommendations || [])
            .find((item) => item.grant.grantId === id);
          setRecommendation(matched || null);
        } else {
          setRecommendation(null);
        }
      })
      .catch((loadError) => {
        setGrant(null);
        setError(loadError.message);
      })
      .finally(() => setLoading(false));
  }, [id, token, user]);

  useEffect(() => {
    load();
  }, [load]);

  const handleScrap = async () => {
    setActionMessage('');
    if (!user) {
      setActionMessage('로그인 후 스크랩할 수 있습니다.');
      return;
    }
    try {
      if (scrapped) {
        await cancelScrap(user.id, token, id);
        setActionMessage('스크랩을 취소했습니다.');
      } else {
        await recordEvent(user.id, token, 'SCRAP', id);
        setActionMessage('스크랩했습니다.');
      }
      await load();
    } catch (actionError) {
      setActionMessage(actionError.message);
    }
  };

  const eligibility = grant ? grant.eligibility : null;
  const ageText = eligibility
    ? (eligibility.targetAgeGroup && eligibility.minAge != null && eligibility.maxAge != null
      ? `${eligibility.targetAgeGroup} (${eligibility.minAge}세 ~ ${eligibility.maxAge}세)`
      : `${eligibility.targetAgeGroup ? `${eligibility.targetAgeGroup} · ` : ''}${boundText(eligibility.minAge, eligibility.maxAge, '세', '원문에 연령 조건이 확인되지 않음')}`)
    : '';
  const incomeText = eligibility
    ? boundText(eligibility.minIncomeBracket, eligibility.maxIncomeBracket, '분위', '원문에 소득분위 조건이 확인되지 않음')
    : '';
  const regionText = !eligibility || !eligibility.targetRegion
    ? '원문에 지역 조건이 확인되지 않음'
    : (eligibility.targetRegion.startsWith('전국') ? `제한 없음 (${eligibility.targetRegion})` : eligibility.targetRegion);

  return (
    <div className={styles.container}>
      <div className={styles.contentWrapper}>
        <h1 className={styles.mainTitle}>{grant ? grant.title : '지원금 상세 정보'}</h1>
        <div className={styles.posterContainer} />
        {loading && <p className={styles.sectionContent}>지원금을 불러오는 중입니다.</p>}
        {error && <p className={styles.sectionContent}>{error}</p>}
        {grant && (
          <div className={styles.detailCard}>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>지원금 제목</h2>
              <p className={styles.sectionContent}>{grant.description}</p>
            </div>
            <div className={styles.separator}></div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>대상 지역</h2>
              <p className={styles.sectionContent}>{regionText}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>대상 연령대</h2>
              <p className={styles.sectionContent}>{ageText}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>소득분위</h2>
              <p className={styles.sectionContent}>{incomeText}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>가구 형태</h2>
              <p className={styles.sectionContent}>{conditionText(eligibility?.householdClarity, eligibility?.householdTypes, HOUSEHOLD_OPTIONS)}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>특수조건</h2>
              <p className={styles.sectionContent}>{conditionText(eligibility?.specialClarity, eligibility?.specialConditions, SPECIAL_OPTIONS)}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>신청 기간</h2>
              <p className={styles.sectionContent}>{periodText(grant)}</p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>자료 구분</h2>
              <p className={styles.sectionContent}>
                {grant.sample ? '테스트용 가상 데이터' : '공개 정책 데이터'}
                {grant.referenceDate ? ` · 기준일 ${grant.referenceDate}` : ''}
                {grant.referenceText && grant.referenceText !== '테스트용 가상 데이터'
                  ? ` · 기준 ${grant.referenceText}`
                  : ''}
              </p>
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>추천 정보</h2>
              <p className={styles.sectionContent}>
                {!user
                  ? '로그인하면 이 지원금의 개인 추천과 스크랩을 사용할 수 있습니다.'
                  : recommendation
                    ? `${recommendation.rank}위 · 점수 ${recommendation.score} · ${statusLabel[recommendation.eligibilityStatus] || recommendation.eligibilityStatus}`
                    : '현재 추천 상위 5건에는 포함되지 않았습니다.'}
              </p>
              {recommendation && <p className={styles.sectionContent}>추천 이유</p>}
              {recommendation && recommendation.reasons.map((reason) => (
                <p key={reason} className={styles.sectionContent}>{reasonText(reason)}</p>
              ))}
            </div>
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>정책 출처</h2>
              {grant.sourceUrl ? (
                <p className={styles.sectionContentLink} onClick={() => window.open(grant.sourceUrl, '_blank')}>
                  {grant.sourceUrl}
                </p>
              ) : (
                <p className={styles.sectionContent}>출처 URL 없음</p>
              )}
            </div>
            <button
              type="button"
              className={styles.scrapButton}
              disabled={!grant.sourceUrl && !grant.link}
              onClick={() => {
                const officialUrl = grant.sourceUrl || grant.link;
                if (officialUrl) {
                  window.open(officialUrl, '_blank', 'noopener,noreferrer');
                }
              }}
            >
              공식 사이트에서 확인하기
            </button>
            <button type="button" className={styles.scrapButton} onClick={handleScrap}>
              {scrapped ? '스크랩 취소' : '스크랩'}
            </button>
            {actionMessage && <p className={styles.sectionContent}>{actionMessage}</p>}
          </div>
        )}
      </div>
    </div>
  );
};

export default DetailPage;
