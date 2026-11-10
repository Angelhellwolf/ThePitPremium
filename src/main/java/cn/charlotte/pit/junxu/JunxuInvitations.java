package cn.charlotte.pit.junxu;

import java.util.*;

/** Recipient and opening nonce are both part of the invitation's authority. */
public final class JunxuInvitations {
    private final Map<UUID, Map<UUID, Invitation>> invitations = new HashMap<>();
    public void send(UUID owner, UUID recipient, UUID session, long expiresAt) {
        invitations.computeIfAbsent(recipient, ignored -> new HashMap<>()).put(owner, new Invitation(owner, recipient, session, expiresAt));
    }
    public Invitation get(UUID recipient, UUID owner, UUID session, long now) {
        Map<UUID,Invitation> inbox = invitations.get(recipient);
        Invitation invitation = inbox == null ? null : inbox.get(owner);
        return invitation != null && invitation.session.equals(session) && now < invitation.expiresAt ? invitation : null;
    }
    public boolean remove(UUID recipient, UUID owner) {
        Map<UUID,Invitation> inbox = invitations.get(recipient);
        return inbox != null && inbox.remove(owner) != null;
    }
    public void invalidate(UUID owner) {
        for (Map<UUID,Invitation> inbox : invitations.values()) inbox.remove(owner);
        invitations.values().removeIf(Map::isEmpty);
    }
    public void expire(long now) {
        for (Map<UUID,Invitation> inbox : invitations.values()) inbox.values().removeIf(invitation -> now >= invitation.expiresAt);
        invitations.values().removeIf(Map::isEmpty);
    }
    public int size() { return invitations.values().stream().mapToInt(Map::size).sum(); }
    public static final class Invitation {
        public final UUID owner, recipient, session;
        public final long expiresAt;
        Invitation(UUID owner, UUID recipient, UUID session, long expiresAt) {
            this.owner=owner; this.recipient=recipient; this.session=session; this.expiresAt=expiresAt;
        }
    }
}
