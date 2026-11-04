import React, { useState } from 'react';
import styles from '../styles/ChatPage.module.css';
import { sendChat } from '../api/moabomApi';

function ChatPage() {
  const [messages, setMessages] = useState([
    { sender: 'bot', text: '안녕하세요! 모아입니다. 무엇을 도와드릴까요?' }
  ]);
  const [input, setInput] = useState('');

  const handleSend = async () => {
    if (!input.trim()) return;

    const userText = input;

    // 1) 사용자 메시지 추가
    setMessages(prev => [...prev, { sender: 'user', text: userText }]);
    setInput('');

    try {
      const data = await sendChat(userText);

      // 3) GPT 답변 추가
      if (data.reply) {
        setMessages(prev => [...prev, { sender: 'bot', text: data.reply }]);
      } else {
        setMessages(prev => [
          ...prev,
          { sender: 'bot', text: "서버에서 응답이 없어요 😢" }
        ]);
      }

    } catch (err) {
      console.log("Chat API error", err);
      setMessages(prev => [
        ...prev,
        { sender: 'bot', text: "서버 통신 오류가 발생했어요 😥" }
      ]);
    }
  };

  return (
    <div className={styles['chat-container']}>
      <h1>😊 모아</h1>

      <div className={styles['chat-box']}>
        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`${styles.msg} ${msg.sender === 'user' ? styles.user : styles.bot}`}
          >
            {msg.text}
          </div>
        ))}
      </div>

      <div className={styles['input-area']}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSend();
          }}
          placeholder="무엇이 궁금하신가요?"
        />
        <button onClick={handleSend}>➤</button>
      </div>
    </div>
  );
}

export default ChatPage;
