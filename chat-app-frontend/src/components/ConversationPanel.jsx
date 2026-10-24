import { FiArrowDown, FiSend } from "react-icons/fi";

export default function ConversationPanel({
  roomId, currentUser, onlineCount, visibleMembers, historyError, box, follow,
  messages, pending, hasOlder, loadingOlder, loadOlder, transmit, newMessages,
  setNewMessages, input, setInput, send,
}) {
  return (
  <section className="conversation-panel">
    <header className="conversation-header">
      <div>
        <p>Conversation</p>
        <h2>Messages</h2>
      </div>
      <div className="conversation-presence">
        <span><i></i>{onlineCount} online</span>
        <small>{visibleMembers.length} {visibleMembers.length === 1 ? "person" : "people"}</small>
      </div>
    </header>

    {historyError && <p role="status" className="history-alert">{historyError}</p>}

    <section ref={box} aria-label="Conversation" className="message-stream"
      onScroll={() => {
        const element = box.current;
        follow.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
        if (follow.current) setNewMessages(false);
      }}>
      {hasOlder && <div className="older-wrap"><button onClick={loadOlder} disabled={loadingOlder} className="older-button">
        {loadingOlder ? "Loading…" : "Load older messages"}</button></div>}

      {!messages.length && !pending.length && !historyError && (
        <div className="empty-conversation">
          <span><FiSend /></span>
          <h3>No messages yet.</h3>
          <p>Send the first one and start the room.</p>
        </div>
      )}

      {messages.map(message => {
        const own = message.sender === currentUser;
        return <article key={message.id} className={`message-row ${own ? "message-own" : "message-other"}`}>
          {!own && <div className="message-avatar">{message.sender?.slice(0, 1)?.toUpperCase()}</div>}
          <div className="message-content">
            <div className="message-meta"><strong>{message.sender}</strong><time dateTime={message.timeStamp}>{new Date(message.timeStamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></div>
            <div className="message-bubble"><p>{message.content}</p></div>
          </div>
        </article>;
      })}

      {pending.map(message => <article key={message.clientMessageId} className="message-row message-own message-pending">
        <div className="message-content">
          <div className="message-meta"><strong>{message.sender}</strong><span>{message.status}</span></div>
          <div className="message-bubble"><p>{message.content}</p></div>
          {message.status === "Failed" && <button type="button" onClick={() => transmit(message)} className="retry-button">Retry send</button>}
        </div>
      </article>)}
    </section>

    {newMessages && <button className="new-message-button" onClick={() => {
      follow.current = true;
      setNewMessages(false);
      box.current.scrollTop = box.current.scrollHeight;
    }}>New messages <FiArrowDown /></button>}

    <form onSubmit={send} className="composer">
      <div className="composer-inner">
        <label><span className="sr-only">Message</span>
          <textarea value={input} onChange={event => setInput(event.target.value)} maxLength={4000} rows={1}
            onKeyDown={event => {
              if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault(); send(event);
              }
            }}
            placeholder={`Message #${roomId}`} />
        </label>
        <button disabled={!input.trim()} className="send-button" aria-label="Send message"><FiSend /></button>
      </div>
    </form>
  </section>
  );
}
