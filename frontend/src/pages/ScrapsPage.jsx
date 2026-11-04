import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { cancelScrap, getBehaviors, getGrants } from '../api/moabomApi';
import { useAuth } from '../auth/AuthContext';
import styles from '../styles/ScrapsPage.module.css';

const ScrapsPage = () => {
  const { user, token } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([getBehaviors(user.id, token), getGrants()])
      .then(([behaviorBody, grantBody]) => {
        if (!active) {
          return;
        }
        const grants = new Map((grantBody.grants || []).map((grant) => [grant.grantId, grant]));
        const scraps = (behaviorBody.behaviors || [])
          .filter((behavior) => behavior.type === 'SCRAP')
          .slice()
          .reverse()
          .map((behavior) => ({
            grantId: behavior.grantId,
            grant: grants.get(behavior.grantId) || null,
          }))
          .filter((item) => item.grant && !item.grant.sample);
        setItems(scraps);
        setError('');
      })
      .catch((loadError) => {
        if (active) {
          setError(loadError.message);
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
  }, [token, user]);

  const handleCancel = async (grantId) => {
    setMessage('');
    try {
      await cancelScrap(user.id, token, grantId);
      setItems((current) => current.filter((item) => item.grantId !== grantId));
      setMessage('스크랩을 취소했습니다.');
    } catch (cancelError) {
      setMessage(cancelError.message);
    }
  };

  return (
    <div className={styles.page}>
      <main className={styles.content}>
        <h1 className={styles.title}>스크랩</h1>
        <p className={styles.lead}>로그인한 계정이 스크랩한 지원금만 표시됩니다.</p>
        {loading && <p className={styles.status}>스크랩 목록을 불러오는 중입니다.</p>}
        {error && <p className={styles.status}>{error}</p>}
        {message && <p className={styles.status}>{message}</p>}
        {!loading && !error && items.length === 0 && (
          <p className={styles.status}>스크랩한 지원금이 없습니다.</p>
        )}
        <ul className={styles.list}>
          {items.map((item) => (
            <li key={item.grantId} className={styles.card}>
              <Link to={`/support/details/${item.grantId}`} className={styles.cardLink}>
                <div className={styles.category}>
                  {item.grant
                    ? `${item.grant.categoryTitle} · ${item.grant.sample ? '예시' : '공개자료'}`
                    : '지원금'}
                </div>
                <div className={styles.cardTitle}>{item.grant ? item.grant.title : item.grantId}</div>
                <div className={styles.detail}>
                  {item.grant ? item.grant.description : '지원금 정보를 찾지 못했습니다.'}
                </div>
              </Link>
              <button type="button" className={styles.cancelButton} onClick={() => handleCancel(item.grantId)}>
                스크랩 취소
              </button>
            </li>
          ))}
        </ul>
      </main>
    </div>
  );
};

export default ScrapsPage;
