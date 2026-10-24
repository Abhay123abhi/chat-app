import { Fragment } from "react";
import { FiArrowDown, FiHash, FiSend } from "react-icons/fi";
import { calendarDayKey, formatDayLabel } from "../services/chatPresentation";

export default function ConversationPanel({
  roomId, currentUser, status, onlineCount, visibleMembers, historyError, box, follow,
  messages, pending, hasOlder, loadingOlder, loadOlder, transmit, newMessages,
  setNewMessages, input, setInput, send,
}) {
  return (
  <section className="conversation-panel">
    <header className="conversation-header">
      <div className="conversation-heading">
        <p className="conversation-eyebrow">LIVE CONVERSATION</p>
        <h2><FiHash aria-hidden="true" /> <span>{roomId}</span></h2>
        <p className="conversation-subtitle">Guest room · Messages stay here when you reconnect</p>
      </div>
      <div className="conversation-presence" aria-label="Room connection and members">
        <span className={`conversation-live-status ${status === "Live" ? "is-live" : "is-connecting"}`}>
          <i aria-hidden="true"></i>{status}
        </span>
        <small>{onlineCount} online · {visibleMembers.length} {visibleMembers.length === 1 ? "person" : "people"}</small>
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

      {messages.map((message, index) => {
        const previous = messages[index - 1];
        const showDay = !previous || calendarDayKey(previous.timeStamp) !== calendarDayKey(message.timeStamp);
        const own = message.sender === currentUser;
        return (
          <Fragment key={message.id}>
            {showDay && (
              <div className="message-day-separator" aria-label={`Messages from ${formatDayLabel(message.timeStamp)}`}>
                <span>{formatDayLabel(message.timeStamp)}</span>
              </div>
            )}
            <article className={`message-row ${own ? "message-own" : "message-other"}`}>
              {!own && <div className="message-avatar" aria-hidden="true">{message.sender?.slice(0, 1)?.toUpperCase()}</div>}
              <div className="message-content">
                <div className="message-meta">
                  <strong>{message.sender}</strong>
                  <time dateTime={message.timeStamp}>
                    {new Date(message.timeStamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  </time>
                </div>
                <div className="message-bubble"><p>{message.content}</p></div>
              </div>
            </article>
          </Fragment>
        );
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
      <div className="composer-eyebrow">MESSAGE IN <strong>#{roomId}</strong></div>
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
      <p className="composer-hint">Enter to send <span aria-hidden="true">·</span> Shift + Enter for a new line</p>
    </form>
  </section>
  );
}
