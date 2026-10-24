import { FiArrowLeft, FiHash, FiUsers, FiWifi } from "react-icons/fi";

function MemberRow({ member, currentUser }) {
  const isCurrent = member.name === currentUser;
  return (
    <div className="member-row" title={member.lastSeen ? `Last seen ${new Date(member.lastSeen).toLocaleString()}` : undefined}>
      <span className="member-avatar">{member.name?.slice(0, 1)?.toUpperCase()}</span>
      <div className="member-copy">
        <strong>{member.name}{isCurrent && <em> you</em>}</strong>
        <span className={member.online ? "member-online" : "member-offline"}>
          <i></i>{member.online ? "Online" : "Offline"}
        </span>
      </div>
    </div>
  );
}

export default function ChatSidebar({ roomId, currentUser, status, onlineMembers, offlineMembers, onlineCount, leave }) {
  return (
  <aside className="chat-sidebar">
    <div className="chat-brand"><span>room</span><b>.</b></div>

    <div className="room-identity">
      <span className="room-icon"><FiHash /></span>
      <div className="room-copy">
        <p>Current room</p>
        <h1>{roomId}</h1>
      </div>
    </div>

    <section className="room-members" aria-label="Room members">
      <div className="members-heading">
        <span><FiUsers /> People</span>
        <small>{onlineCount} online</small>
      </div>

      <div className="member-list">
        {onlineMembers.length > 0 && (
          <div className="member-group">
            <p className="member-group-label"><span>Online</span><b>{onlineMembers.length}</b></p>
            {onlineMembers.map(member => <MemberRow key={`online-${member.name}`} member={member} currentUser={currentUser} />)}
          </div>
        )}

        {offlineMembers.length > 0 && (
          <div className="member-group">
            <p className="member-group-label"><span>Offline</span><b>{offlineMembers.length}</b></p>
            {offlineMembers.map(member => <MemberRow key={`offline-${member.name}`} member={member} currentUser={currentUser} />)}
          </div>
        )}
      </div>
    </section>

    <div className="sidebar-bottom">
      <span className={`connection-status status-${status.toLowerCase().replaceAll(" ", "-")}`}><FiWifi /> {status}</span>
      <button type="button" onClick={leave} className="leave-button"><FiArrowLeft /> Leave room</button>
    </div>
  </aside>
  );
}
