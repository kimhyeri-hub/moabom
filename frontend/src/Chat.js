import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Chat.css';

function ChatPage() {
  const navigate = useNavigate(); 

  // 기본 메시지
  const [messages, setMessages] = useState([
    { sender: 'bot', text: '안녕하세요 모아입니다.\n무엇을 도와드릴까요?' }
  ]);

  const [input, setInput] = useState('');

  // 메시지 추가 함수
  const sendMessage = (sender, text) => {
    setMessages(prev => [...prev, { sender, text }]);
  };

  // 전송 기능
  const handleSend = () => {
    if (!input.trim()) return;

    sendMessage('user', input);
    setInput('');

    // 임시 봇 응답
    setTimeout(() => {
      sendMessage('bot', '답변 준비 중입니다');
    }, 300);
  };

  return (

    <div className="chat-container">

      {/* 메시지 영역 */}
      <div className="chat-box">
        {messages.map((msg, idx) => (
          <div key={idx} className={`msg ${msg.sender}`}>
            {msg.text}
          </div>
        ))}
      </div>

      {/* 입력 + 뒤로가기 버튼 영역 */}
      <div className="input-section">

        {/* 뒤로가기 버튼만 남김 */}
        <div className="back-area">
          <button className="back-btn" onClick={() => navigate(-1)}>
          돌아가기
          </button>
        </div>

        {/* 입력창 */}
        <div className="input-area">
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
