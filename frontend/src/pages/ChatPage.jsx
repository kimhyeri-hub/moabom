import React, { useState } from 'react';
// ⭐ 1. CSS 모듈로 임포트 경로를 수정합니다. ⭐
import styles from '../styles/ChatPage.module.css'; 

function ChatPage() {
  const [messages, setMessages] = useState([
    { sender: 'bot', text: '안녕하세요! 모아입니다. \n 도움이 필요하신가요?' }
  ]);
  const [input, setInput] = useState('');

  const handleSend = () => {
    if (!input.trim()) return;
    setMessages([...messages, { sender: 'user', text: input }]);
    setInput('');

    setTimeout(() => {
      setMessages(prev => [...prev, { sender: 'bot', text: '답변 준비 중이에요!' }]);
    }, 300);
  };

  return (
    // ⭐ 2. 모든 className을 styles.클래스명 으로 수정합니다. ⭐
    <div className={styles['chat-container']}>
      <h1>😊 모아</h1>

      {/* 채팅 메시지 영역 */}
      <div className={styles['chat-box']}>
        {messages.map((msg, idx) => (
          <div key={idx} className={`${styles.msg} ${msg.sender === 'user' ? styles.user : styles.bot}`}>
            {/* Note: 'user'와 'bot' 클래스도 styles에서 가져와야 함 */}
            {msg.text}
          </div>
        ))}
      </div>

      {/* 입력창 + FAQ 버튼 */}
      <div className={styles['input-section']}>
      
        <div className={styles['faq-buttons']}>
          <button>자주 묻는 질문 1</button>
          <button>자주 묻는 질문 2</button>
          <button className={styles['back-btn']}>돌아가기</button>
        </div>

        <div className={styles['input-area']}>
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              handleSend();
            }
          }}
          placeholder="무엇이 궁금하신가요?"
        />
          <button onClick={handleSend}>➤</button>
        </div>

      </div>
    </div>
  );
}

export default ChatPage;