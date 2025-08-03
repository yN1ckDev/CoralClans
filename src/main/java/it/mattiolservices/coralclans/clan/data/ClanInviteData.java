package it.mattiolservices.coralclans.clan.data;

import it.mattiolservices.coralclans.bootstrap.CoralClans;

import java.time.LocalDateTime;

public final class ClanInviteData {
    private final int clanId;
    private final String invitedName;
    private final String inviterUuid;
    private final LocalDateTime expiresAt;

    public ClanInviteData(int clanId, String invitedName, String inviterUuid) {
        this.clanId = clanId;
        this.invitedName = invitedName;
        this.inviterUuid = inviterUuid;
        this.expiresAt = LocalDateTime.now().plusMinutes(CoralClans.get().getConfigManager().getConfig().getLong("Clans.invite-expiry"));
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public int getClanId() { return clanId; }
    public String getInvitedName() { return invitedName; }
    public String getInviterUuid() { return inviterUuid; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
